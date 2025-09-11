DB2API
======

DB2API is a lightweight Spring Boot service that provides a **uniform REST API** 
for executing queries against different databases such as:

- **SQL databases** (MySQL, PostgreSQL, Oracle, SQL Server, etc.)
- **MongoDB**
- **Redis**

With DB2API, applications can access heterogeneous databases via a **single, consistent HTTP interface**.

---

Installation (tested on ubuntu server 24.04.3 LTS)
------------

1. Clone the repository:

   .. code-block:: bash

      git clone https://github.com/opencelium/db2api.git
      cd db2api

2. Build the project with Maven:

   .. code-block:: bash

      mvn clean package

3. Activate the systemd file and add it to the Autostart:

   .. code-block:: bash

      ln -s conf/db2api.service /etc/systemd/system/db2api.service
      systemctl daemon-reload
      systemctl enable db2api

5. Start the service:

   .. code-block:: bash

      systemctl enable db2api

The service runs by default on port **8080**.

.. note::
	If you like to use another port, add in the db2api.server config, at the end of the ExecStart config " --server.port=9090".
  See example below :
	
	.. code-block:: sh
		:linenos:
	
		ExecStart=/usr/bin/java -jar /opt/opencelium/services/db2api/target/db2api-1.0.0.jar --server.port=9000
		
	do not forgot to reload the systemd config:	
			
	.. code-block:: sh
		:linenos:	
	
		systemctl daemon-reload
		systemctl enable db2api
  

---

API Endpoints
-------------

1. **Connection Test**

   .. code-block:: http

      GET /connect-test

   Required HTTP headers:

   - ``X-DB-Engine``   → Database engine (mysql, postgres, mongodb, redis, …)
   - ``X-DB-Host``     → Database host
   - ``X-DB-Port``     → (optional) Database port
   - ``X-DB-Name``     → Database/schema name
   - ``X-DB-User``     → (optional) Username
   - ``X-DB-Password`` → (optional) Password
   - ``X-DB-Options``  → (optional) Extra connection parameters

   Example response:

   .. code-block:: json

      {
        "success": true,
        "engine": "postgres",
        "databaseProductName": "PostgreSQL",
        "databaseProductVersion": "14.5"
      }

---

2. **Execute Query**

   .. code-block:: http

      POST /query
      Content-Type: application/json

   Request body:

   .. code-block:: json

      {
        "query": "SELECT * FROM users WHERE id = ?",
        "params": [123],
        "maxRows": 100
      }

   Response example (SQL):

   .. code-block:: json

      {
        "success": true,
        "rowCount": 1,
        "rows": [
          {
            "id": 123,
            "name": "Alice"
          }
        ]
      }

---

MongoDB Usage
-------------

- ``query`` = collection name
- ``params`` = filter as JSON object
- ``fields`` = list of fields to return
- ``maxRows`` = max number of documents

Example request:

.. code-block:: json

   {
     "query": "users",
     "params": [
       { "age": { "$gt": 25 } }
     ],
     "fields": ["name", "email", "_id"],
     "maxRows": 10
   }

Example response:

.. code-block:: json

   {
     "success": true,
     "rowCount": 2,
     "rows": [
       { "name": "Alice", "email": "alice@example.com", "_id": "64e..." },
       { "name": "Bob", "email": "bob@example.com", "_id": "64f..." }
     ]
   }

---

Redis Usage
-----------

- ``query`` = Redis key
- ``params`` are ignored

Example request:

.. code-block:: json

   {
     "query": "myKey"
   }

Example response:

.. code-block:: json

   {
     "success": true,
     "key": "myKey",
     "value": "Hello World"
   }

---

License
-------

DB2API is released under the Apache-2.0 License.
