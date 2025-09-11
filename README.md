# DB2API

DB2API ist ein einfacher Spring Boot API-Server, der den Zugriff auf verschiedene Datenbanken über HTTP ermöglicht.

Unterstützte Datenbanken:

* **MongoDB** – mit Filter und Feldselektion (Projections)
* **Redis** – Zugriff auf Keys
* **JDBC-kompatible Datenbanken** – z.B. MySQL, PostgreSQL, SQL Server, Oracle, etc.

## Installation / Setup

1. Repository klonen:

```
git clone https://github.com/opencelium/db2api.git
cd db2api
```

2. Projekt bauen:

```
mvn clean package
```

3. Anwendung starten:

```
java -jar target/db2api-0.0.1-SNAPSHOT.jar
```

Die API läuft standardmäßig auf **[http://localhost:8080](http://localhost:8080)**.

## Endpoints

Connect Test

````

**URL:** ``GET /connect-test``  

**Headers:**

+----------------+-----------------------------------------------+
| Header         | Beschreibung                                 |
+================+===============================================+
| X-DB-Engine    | Datenbank-Typ (`mongodb`, `redis`, `mysql`)  |
+----------------+-----------------------------------------------+
| X-DB-Host      | Hostname / IP                                 |
+----------------+-----------------------------------------------+
| X-DB-Port      | Port (optional)                               |
+----------------+-----------------------------------------------+
| X-DB-Name      | Datenbank-Name                                |
+----------------+-----------------------------------------------+
| X-DB-User      | Username (optional)                           |
+----------------+-----------------------------------------------+
| X-DB-Password  | Passwort (optional)                           |
+----------------+-----------------------------------------------+
| X-DB-Options   | Zusätzliche Optionen (optional)               |
+----------------+-----------------------------------------------+

**Response Beispiel:**

```
{
  "requestedAt": "2025-09-11T12:34:56Z",
  "success": true,
  "engine": "mongodb",
  "uri": "mongodb://user:pass@localhost:27017/testdb"
}
```

Query
~~~~~

**URL:** ``POST /query``  
**Content-Type:** ``application/json``

**Request Body DTO (`QueryRequest`):**

+---------+---------------------------+----------------------------------------------------+
| Feld    | Typ                       | Beschreibung                                      |
+=========+===========================+====================================================+
| query   | String                    | Name der Collection (MongoDB), SQL Query (JDBC) oder Key (Redis) |
+---------+---------------------------+----------------------------------------------------+
| params  | List[Map[String,Object]]  | Filter für MongoDB, PreparedStatement-Parameter für JDBC |
+---------+---------------------------+----------------------------------------------------+
| fields  | List[String]              | Welche Felder bei MongoDB zurückgegeben werden sollen |
+---------+---------------------------+----------------------------------------------------+
| maxRows | Integer                   | Max. Anzahl der zurückgegebenen Zeilen            |
+---------+---------------------------+----------------------------------------------------+

MongoDB Beispiel
````

**Request:**

```
POST /query
{
  "query": "users",
  "params": [
    { "age": { "$gte": 30 }, "status": "active" }
  ],
  "fields": ["name", "email"],
  "maxRows": 50
}
```

**Response:**

```
{
  "success": true,
  "rowCount": 2,
  "rows": [
    { "name": "Alice", "email": "alice@example.com" },
    { "name": "Bob", "email": "bob@example.com" }
  ]
}
```

> `_id` wird nur zurückgegeben, wenn es in `fields` enthalten ist.

JDBC Beispiel (z.B. MySQL)

````

**Request:**

```
POST /query
{
  "query": "SELECT id, name, email FROM users WHERE age >= ?",
  "params": [
    { "age": 30 }
  ],
  "maxRows": 100
}
```

**Response:**

```
{
  "success": true,
  "rowCount": 2,
  "rows": [
    { "id": 1, "name": "Alice", "email": "alice@example.com" },
    { "id": 2, "name": "Bob", "email": "bob@example.com" }
  ]
}
```

Redis Beispiel
~~~~~~~~~~~~~~

**Request:**

```
POST /query
{
  "query": "mykey"
}
```

**Response:**

```
{
  "success": true,
  "key": "mykey",
  "value": "Hello World"
}
```

Quick Start / curl Beispiele
---------------------------

MongoDB:

```
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -H "X-DB-Engine: mongodb" \
  -H "X-DB-Host: localhost" \
  -H "X-DB-Port: 27017" \
  -H "X-DB-Name: testdb" \
  -d '{"query":"users","params":[{"age":{"$gte":30}}],"fields":["name","email"],"maxRows":10}'
```

JDBC (z.B. MySQL):

```
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -H "X-DB-Engine: mysql" \
  -H "X-DB-Host: localhost" \
  -H "X-DB-Port: 3306" \
  -H "X-DB-Name: testdb" \
  -H "X-DB-User: root" \
  -H "X-DB-Password: secret" \
  -d '{"query":"SELECT id,name,email FROM users WHERE age >= ?","params":[{"age":30}],"maxRows":10}'
```

Redis:

```
curl -X POST http://localhost:8080/query \
  -H "Content-Type: application/json" \
  -H "X-DB-Engine: redis" \
  -H "X-DB-Host: localhost" \
  -H "X-DB-Port: 6379" \
  -d '{"query":"mykey"}'
```

Hinweise
---------

- Alle Datenbankzugriffe werden über HTTP-Header für Engine, Host, Port, User, Password gesteuert.  
- Für MongoDB kann ein beliebiges Filterobjekt als JSON über `params` übergeben werden.  
- Feldselektion (`fields`) bei MongoDB ermöglicht das gezielte Ein-/Ausschließen von Feldern.  
- Bei JDBC wird `params` automatisch in PreparedStatement-Parameter konvertiert.

License
-------

Apache-2.0 license 

````
