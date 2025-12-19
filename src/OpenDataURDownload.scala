package lv.opendata.ur

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.Logger
import org.apache.pekko.actor.ActorSystem
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.client.RequestBuilding.{Get, Head}
import org.apache.pekko.http.scaladsl.model.HttpResponse
import org.apache.pekko.http.scaladsl.model.headers.HttpEncodings.{compress, deflate, gzip}
import org.apache.pekko.http.scaladsl.model.headers.{`Accept-Encoding`, `Last-Modified`}
import org.apache.pekko.stream.scaladsl.FileIO
import org.slf4j.LoggerFactory
import org.tresql._

import java.io.{File, FileInputStream}
import java.sql.{DriverManager, Timestamp}
import java.time.LocalDateTime
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object URLoad {
  private val logger = Logger(LoggerFactory.getLogger("ur.opendata.loader"))

  def downloadFile(url: String, currentVersion: LocalDateTime)(
    implicit as: ActorSystem): Future[(File, LocalDateTime, String)] = {
    def versionFromHeader(resp: HttpResponse) = {
      try {
        if (resp.status.isSuccess()) {
          resp.header[`Last-Modified`]
            .map(lm => LocalDateTime.parse(lm.date.toIsoDateTimeString()))
            .getOrElse(sys.error(s"Last-Modified header was not found in response for '$url'."))
        } else sys.error(s"HTTP error: ${resp.status}")
      } finally resp.discardEntityBytes()
    }
    implicit val ec: ExecutionContext = as.dispatcher
    Http().singleRequest(Head(url)).map(versionFromHeader).flatMap { lastModified =>
      if (lastModified.isAfter(currentVersion)) {
        logger.debug(s"New data found: $lastModified")
        val req = Get(url).withHeaders(List(`Accept-Encoding`(gzip, compress, deflate)))
        logger.debug(s"Downloading UR data...")
        Http().singleRequest(req).flatMap { resp =>
          logger.debug(s"UR data: $resp")
          val tmpFile = File.createTempFile("ur-register", ".csv")
          logger.debug(s"File created for ur data: $tmpFile")
          resp.entity.dataBytes.runWith(FileIO.toPath(tmpFile.toPath))
            .map {_ =>
              logger.debug(s"UR data file downloaded: $tmpFile")
              (tmpFile, lastModified, resp.entity.contentType.charsetOption.map(_.value).getOrElse("UTF-8"))
            }
        }
      } else Future.successful((null, null, null))
    }
  }

  def loadData(url: String)(implicit resources: Resources, as: ActorSystem): Future[Unit] = {
    implicit val ec: ExecutionContext = as.dispatcher
    //get current data version
    Query("""`create table if not exists ur_version (version timestamp not null)`""")
    val currentVersion = Query("ur_version{version}").uniqueOption[LocalDateTime].getOrElse {
      val version = LocalDateTime.parse("2025-01-01T00:00:00")
      Query("+ur_version{version = ?}", Timestamp.valueOf(version))
      version
    }
    logger.debug(s"Current data version: $currentVersion. Checking for fresh data...")
    //download file if current version is older then open data version
    downloadFile(url, currentVersion).map {
      case (file, version, charset) if file != null => try {
        //create temptable
        Query("`drop table if exists ur_data_tmp`")
        Query(
          """`
            |create table ur_data_tmp(
            |  regcode text,
            |  sepa text,
            |  name text,
            |  name_before_quotes text,
            |  name_in_quotes text,
            |  name_after_quotes text,
            |  without_quotes text,
            |  regtype text,
            |  regtype_text text,
            |  type text,
            |  type_text text,
            |  registered text,
            |  terminated text,
            |  closed text,
            |  address text,
            |  index text,
            |  addressid text,
            |  region text,
            |  city text,
            |  atvk text,
            |  reregistration_term text
            |)
            |`""".stripMargin)
        //load data
        val copyManager =
          new org.postgresql.copy.CopyManager(resources.conn.unwrap(classOf[org.postgresql.core.BaseConnection]))
        logger.debug(s"Loading ur data from file: $file, charset: $charset")
        val in = new FileInputStream(file)
        try {
          copyManager.copyIn(
            s"copy ur_data_tmp from stdin with (format csv, header true, delimiter ';', encoding '$charset')",
            in
          )
        } finally in.close()
        logger.debug(s"Loading ur data from file: $file done.")
        //create primary key
        Query("`alter table ur_data_tmp add primary key (regcode)`")
        //drop current table
        Query("`drop table if exists ur_data`")
        //rename table
        Query("`alter table ur_data_tmp rename to ur_data`")
        //update version
        Query("=ur_version{version = ?}", version)
        resources.conn.commit()
      } finally file.delete()
      case _ => logger.debug(s"No new UR data")
    }
  }

  /** See reference.conf */
  def loadData(config: Config)(implicit as: ActorSystem): Future[Unit] = {
    val url = config.getString("url")
    val dbConf = config.getConfig("db")
    val (dbUrl, usr, pwd) = (dbConf.getString("url"), dbConf.getString("usr"), dbConf.getString("pwd"))
    Class.forName("org.postgresql.Driver")
    val conn = DriverManager.getConnection(dbUrl, usr, pwd)
    conn.setAutoCommit(false)
    implicit val ec: ExecutionContext = as.dispatcher
    implicit val res: Resources = Resources()
      .withMacros(new Macros)
      .withConn(conn)
    try loadData(url).andThen {
      case Success(_) =>
        logger.debug(s"UR data import done")
        conn.close()
      case Failure(ex) =>
        logger.error(s"UR download error", ex)
        conn.close()
    } catch {
      case e: Throwable =>
        logger.error(s"UR download error", e)
        conn.close()
        Future.failed(e)
    }
  }

  def main(args: Array[String]): Unit = {
    implicit val as: ActorSystem = ActorSystem("ur-open-data")
    loadData(ConfigFactory.load().getConfig("ur.open-data"))
  }
}
