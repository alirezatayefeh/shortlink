# Shortlink

A URL shortener built with Java 21, Spring Boot, and PostgreSQL.
Currently includes the application skeleton and local database setup; link APIs are not implemented yet.

## Requirements

- JDK 21
- Docker with Docker Compose

## Run locally

From the project root, start PostgreSQL:

```powershell
docker compose up -d --wait
```

Run the application on Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw spring-boot:run`.
The server listens on port 8080. A request to `/` returns 404 until an endpoint is implemented.

The default database is `shortlink` at `localhost:5432`, with username `shortlink`
and password `shortlink_dev`. These credentials are for local development only.
Override the application connection with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
environment variables when connecting to another database.

Flyway manages schema migrations; Hibernate validates entity mappings rather than
creating tables. No application tables or migrations exist yet.

## Verify

```powershell
.\mvnw.cmd verify
```

Docker must be running. Tests start an isolated PostgreSQL 17 container through
Testcontainers and do not use the Compose database.

## Stop

Stop the application with Ctrl+C, then stop the database:

```powershell
docker compose down
```

Database data remains in the named volume. `docker compose down -v` also deletes
that data. PostgreSQL initialization settings apply only when the volume is empty;
changing the password in Compose does not update an existing database user.
