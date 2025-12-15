ThisBuild / scalaVersion     := "2.13.17"
ThisBuild / version          := "1.0.0"

val pekkoV    = "1.2.1"
val pekkoHttpV= "1.2.0"

lazy val root = (project in file("."))
  .settings(
    name := "ur-open-data",
    libraryDependencies ++= Seq(
      "org.apache.pekko"           %% "pekko-actor"           % pekkoV,
      "org.apache.pekko"           %% "pekko-http"            % pekkoHttpV,
      "org.apache.pekko"           %% "pekko-slf4j"           % pekkoV,
      "org.apache.pekko"           %% "pekko-stream"          % pekkoV,
      "org.tresql"                 %% "tresql"                % "13.1.0",
      "org.postgresql"              % "postgresql"            % "42.7.8",
      "com.typesafe.scala-logging" %% "scala-logging"         % "3.9.5",
      "ch.qos.logback"              % "logback-classic"       % "1.5.20",
    ),
    assemblySettings,
  )

import sbtassembly.AssemblyPlugin.autoImport.assembly
import sbtassembly.MergeStrategy

lazy val assemblySettings = Seq(
  assembly / assemblyMergeStrategy := {
    case "module-info.class" =>
      MergeStrategy.discard
    case x =>
      val oldStrategy = (assembly / assemblyMergeStrategy).value
      oldStrategy(x)
  }
)
