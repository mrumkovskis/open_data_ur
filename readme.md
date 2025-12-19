Utility downloads UR data from open data portal if new data are available and loads them into postgres database. Data version is determined after HTTP `'Last-Modified'` header value.

Sample command to start postgres database in docker:
`docker run -d --rm --name ur-open-data -p 5432:5432 -e POSTGRES_DB=ur_data -e POSTGRES_USER=ur -e POSTGRES_PASSWORD=ur -e POSTGRES_HOST_AUTH_METHOD=trust postgres`

To assembly production artifact run:
```./mill assembly```

Run example:
```
java -classpath ./src/resources:./out/assembly.dest/out.jar -DUR_OPENDATA_DB="jdbc:postgresql://localhost/ur_data" -DUR_OPENDATA_USR=ur -DUR_OPENDATA_PWD=ur lv.opendata.ur.URLoad
```

To run from within application:
```
val config: Config = ... get config
implicit val as: ActorSystem = ... initialize actor system
lv.opendata.ur.URLoad.loadData(config)
```

Database tables
```
Table "ur_version"
 Column  |            Type             | Collation | Nullable | Default 
---------+-----------------------------+-----------+----------+---------
 version | timestamp without time zone |           | not null | 

 Table "ur_data"
       Column        | Type | Collation | Nullable | Default 
---------------------+------+-----------+----------+---------
 regcode             | text |           | not null | 
 sepa                | text |           |          | 
 name                | text |           |          | 
 name_before_quotes  | text |           |          | 
 name_in_quotes      | text |           |          | 
 name_after_quotes   | text |           |          | 
 without_quotes      | text |           |          | 
 regtype             | text |           |          | 
 regtype_text        | text |           |          | 
 type                | text |           |          | 
 type_text           | text |           |          | 
 registered          | text |           |          | 
 terminated          | text |           |          | 
 closed              | text |           |          | 
 address             | text |           |          | 
 index               | text |           |          | 
 addressid           | text |           |          | 
 region              | text |           |          | 
 city                | text |           |          | 
 atvk                | text |           |          | 
 reregistration_term | text |           |          | 
Indexes:
    "ur_data_tmp_pkey" PRIMARY KEY, btree (regcode)
```

During loading table `ur_data_tmp` is used.
