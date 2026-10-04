# Shortlink

A URL shortener built with Java 21, Spring Boot, and PostgreSQL.
Creates short links, stores them in PostgreSQL, and redirects visitors to their original URLs.

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
The server listens on port 8080. Swagger UI is available at
`http://localhost:8080/swagger-ui/index.html`; its URL is logged at startup.
There is no homepage at `/`.

The default database is `shortlink` at `localhost:5432`, with username `shortlink`
and password `shortlink_dev`. These credentials are for local development only.
Override the application connection with `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`
environment variables when connecting to another database.

Flyway manages schema migrations; Hibernate validates entity mappings rather than
creating tables. The first migration creates the `links` table.

Set `APP_BASE_URL` to the public address used in generated short links.
It defaults to `http://localhost:8080`; update it when changing the port, context
path, or deployment domain.

## API

- `POST /api/links`: send `{"originalUrl":"https://example.com"}` to create a link.
  Returns 201 with `shortCode`, `shortUrl`, `originalUrl`, and `createdAt`.
- `GET /{shortCode}`: returns a 302 redirect, or 404 for an unknown code.
- Invalid input returns 400. Only absolute HTTP/HTTPS URLs without credentials
  are accepted, up to 2048 characters. Destination availability is not checked.

Controllers handle HTTP; the service creates response DTOs and coordinates
persistence. A separate generator creates random codes, and database uniqueness
conflicts are retried up to five times. Exception handling uses Problem Detail responses.

## Verify

```powershell
.\mvnw.cmd verify
```

Docker must be running. Tests start an isolated PostgreSQL 17 container through
Testcontainers and do not use the Compose database.
Tests cover creation, persistence, redirects, invalid input, missing codes,
and retries after real database uniqueness conflicts.

## Stop

Stop the application with Ctrl+C, then stop the database:

```powershell
docker compose down
```

Database data remains in the named volume. `docker compose down -v` also deletes
that data. PostgreSQL initialization settings apply only when the volume is empty;
changing the password in Compose does not update an existing database user.
