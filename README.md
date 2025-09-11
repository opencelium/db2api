# DB2API

DB2API is a lightweight Spring Boot service that provides a **uniform REST API**  
for executing queries against different databases such as:

- **SQL databases** (MySQL, PostgreSQL, Oracle, SQL Server, etc.)
- **MongoDB**
- **Redis**

With DB2API, applications can access heterogeneous databases via a **single, consistent HTTP interface**.

---

## Table of Contents

- [Supported DB Engines](#supported-db-engines)
- [Installation](#installation-tested-on-ubuntu-server-24043-lts)
- [API Endpoints](#api-endpoints)
  - [Connection Test](#1-connection-test)
  - [Execute Query](#2-execute-query)
  - [MongoDB Usage](#mongodb-usage)
  - [Redis Usage](#redis-usage)
- [Logging](#logging)
- [License](#license)

---

## Supported DB Engines

DB2API currently supports the following SQL and NoSQL engines:

- **MySQL / Connector/J**  
- **MariaDB**  
- **PostgreSQL / Postgres / pgjdbc_ng**  
- **Microsoft SQL Server / MSSQL**  
- **Oracle**  
- **Apache Derby**  
- **H2**  
- **HSQLDB**  
- **Firebird / Jaybird**  
- **IBM DB2 / ibm_jcc**  
- **IBM Informix / informix**  
- **SAP HANA**  
- **SQLite / Xerial**  
- **OrientDB**  
- **MongoDB**  
- **Redis**  

These are the engines that can currently be used with DB2API.

---

## Installation (tested on Ubuntu Server 24.04.3 LTS)

1. Clone the repository:

   ```bash
   git clone https://github.com/opencelium/db2api.git
   cd db2api
   ```

2. Build the project with Maven:

   ```bash
   mvn clean package
   ```

3. Activate the systemd file and add it to the Autostart:

   ```bash
   ln -s "$(pwd)"/conf/db2api.service /etc/systemd/system/db2api.service
   systemctl daemon-reload
   systemctl enable db2api
   ```

4. Start the service:

   ```bash
   systemctl start db2api
   ```

The service runs by default on port **8080**.

> **Note:**  
> If you want to use another port, add in the `db2api.server` config at the end of the `ExecStart` command:  
> `--server.port=9090`  
>
> Example:
>
> ```sh
> ExecStart=/usr/bin/java -jar /opt/opencelium/services/db2api/target/db2api-1.0.0.jar --server.port=9000
> ```
>
> Don’t forget to reload the systemd config:
>
> ```sh
> systemctl daemon-reload
> systemctl restart db2api
> ```

---

## API Endpoints

### 1. Connection Test

```http
GET /connect-test
```

**Required HTTP headers:**

- `X-DB-Engine`   → Database engine (mysql, postgres, mongodb, redis, …)  
- `X-DB-Host`     → Database host  
- `X-DB-Port`     → (optional) Database port  
- `X-DB-Name`     → Database/schema name  
- `X-DB-User`     → (optional) Username  
- `X-DB-Password` → (optional) Password  
- `X-DB-Options`  → (optional) Extra connection parameters  

**Example response:**

```json
{
  "success": true,
  "engine": "postgres",
  "databaseProductName": "PostgreSQL",
  "databaseProductVersion": "14.5"
}
```

---

### 2. Execute Query

```http
POST /query
Content-Type: application/json
```

**Request body:**

```json
{
  "query": "SELECT * FROM users WHERE id = ?",
  "params": [123],
  "maxRows": 100
}
```

**Response example (SQL):**

```json
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
```

---

## MongoDB Usage

- `query` = collection name  
- `params` = filter as JSON object  
- `fields` = list of fields to return  
- `maxRows` = max number of documents  

**Example request:**

```json
{
  "query": "users",
  "params": [
    { "age": { "$gt": 25 } }
  ],
  "fields": ["name", "email", "_id"],
  "maxRows": 10
}
```

**Example response:**

```json
{
  "success": true,
  "rowCount": 2,
  "rows": [
    { "name": "Alice", "email": "alice@example.com", "_id": "64e..." },
    { "name": "Bob", "email": "bob@example.com", "_id": "64f..." }
  ]
}
```

---

## Redis Usage

- `query` = Redis key  
- `params` are ignored  

**Example request:**

```json
{
  "query": "myKey"
}
```

**Example response:**

```json
{
  "success": true,
  "key": "myKey",
  "value": "Hello World"
}
```

---

## Logging

The service logs can be viewed via **journalctl**:

```bash
journalctl -xe -u db2api -o cat -f
```

This command shows the live logs (`-f`) of the `db2api` service.

---

## License

DB2API is released under the [Apache-2.0 License](LICENSE).
