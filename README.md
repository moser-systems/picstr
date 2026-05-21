<div align="center">
  <img src="/src/main/resources/static/logo_800x800_trans.png" width="200" />

# PicStr - Picture and Photo Storage Application

</div>

A **mobile-first direct photo upload and management application** built with Spring Boot. Upload photos from a mobile device or desktop, organise them with categories and tags, view them in a responsive gallery.

Quickstart:
See [Running](#running) for a quick way to run the application locally with an embedded H2 database and local storage.

## Index
- [Why](#why)
- [Features](#features)
- [Technology Stack](#technology-stack)
- [Building](#building)
- [Running](#running)
- [Configuration Reference](#configuration-reference)
  - [Authentication (OAuth2 / OpenID Connect)](#authentication-oauth2--openid-connect)
- [Example docker-compose](#example-docker-compose)
- [Development Tips](#development-tips)
- [Contributing](#contributing)
- [License](#license)
- [Future Enhancements](#future-enhancements)

---

## Why

### 1. Privacy
Photos will **not be stored on the device** but directly uploaded to the backend, so it won't take up local storage space and respects privacy by not leaving traces in the device's gallery.
### Accessibility
Anyone from your organization can access photos with a web browser.
### 2. Organization
Organize photos with categories and tags, and view them in a responsive gallery with pagination.
### 3. Open-source and self-hosted
Self-hosted and open-source, so you have full control over your data and can contribute to the project.

---

## Features

### Core
- **Photo upload** – supports JPEG, PNG, GIF, WebP and HEIC/HEIF (auto-converted to JPEG on upload)
- **Camera capture** – uses for direct mobile camera access
- **Automatic thumbnail generation** 
- **GPS extraction** – photos with GPS data are shown on a map view
- **Category & tag organisation** – colour-coded badges (Tabler palette) for both categories and tags

### Archive & lifecycle
- **Soft-delete (archive)** – photos can be archived and are excluded from all public views
- **Restore** – archived photos can be restored to active state
- **Archive purge** – a scheduled job permanently deletes (including storage files) archived photos older than a configurable retention window

### Storage backends

| Backend | Description                                                     |
|---------|-----------------------------------------------------------------|
| `s3` | AWS S3-compatible (AWS, MinIO, Ceph, …)                         |
| `local` | Local filesystem — ideal for development and single-node setups |
| `ftp` | Old plain FTP server                                            |

### Database support

| Engine | JDBC driver | Flyway vendor name |
|-------|------------|-------------------|
| MariaDB / MySQL | `org.mariadb.jdbc:mariadb-java-client` | `mariadb` |
| PostgreSQL | `org.postgresql:postgresql` | `postgresql` |
| H2 | `com.h2database:h2` | `h2` |

Flyway migrations are located in `src/main/resources/db/migration/{vendor}/`.

---

## Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Java 25 |
| Framework | Spring Boot 4 |
| Persistence | Spring Data JPA + Hibernate |
| Database migrations | Flyway 12 |
| Templates | Thymeleaf + Thymeleaf Layout Dialect |
| Frontend assets | Tabler UI (built via Node/npm) |
| Thumbnail engine | GraphicsMagick (via im4java) |
| S3 client | AWS SDK v2 |
| FTP client | Apache Commons Net |
| Image metadata | metadata-extractor (EXIF/GPS) |
| HEIC/HEIF support | HeicHeifConversionService |
| Container runtime | Eclipse Temurin 25 JRE Alpine |

---

## Building

**Prerequisites:**
- Java 25
- Maven wrapper (`./mvnw`) is included
- Node.js ≥ 22 (auto-installed by the frontend-maven-plugin during the Maven build)
- GraphicsMagick (`gm` binary) on the host for thumbnail generation

```bash
# Full build (compiles Java, downloads Node, runs npm build)
./mvnw clean package

# Skip tests
./mvnw clean package -DskipTests

# Run tests only
./mvnw test
```

---

## Running

### Java / JDK

#### Local development (H2 + local storage)

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="\
    --spring.datasource.url=jdbc:h2:file:./data/picstr-dev;MODE=MariaDB;DATABASE_TO_LOWER=TRUE \
    --spring.datasource.username=sa \
    --spring.datasource.password= \
    --app.storage.type=local \
    --app.storage.local.base-path=./data/uploads"
```

#### Local development (MariaDB + local storage)

```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="\
    --spring.datasource.url=jdbc:mariadb://localhost:3306/picstr?createDatabaseIfNotExist=true \
    --spring.datasource.username=picstr \
    --spring.datasource.password=picstr \
    --app.storage.type=local"
```

#### Running the packaged jar and ENV vars

```bash
java -jar target/picstr-*.jar \
  --DB_URL=jdbc:mariadb://db:3306/picstr?createDatabaseIfNotExist=true \
  --DB_USER=picstr \
  --DB_PASSWORD=secret \
  --APP_STORAGE_TYPE=s3 \
  --APP_STORAGE_S3_ENDPOINT=http://minio:9000 \
  --APP_STORAGE_S3_BUCKET=picstr-images \
  --APP_STORAGE_S3_ACCESS_KEY=minioadmin \
  --APP_STORAGE_S3_SECRET_KEY=minioadmin
```

### Container Images

####  Quick testing with H2 and local storage:

```bash
podman run -p 8080:8080 \
-e 'DB_URL=jdbc:h2:file:./data/picstr-dev;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH' \
-e DB_USER=sa \
-e DB_PASSWORD= \
-e APP_STORAGE_TYPE=local \
-e APP_STORAGE_LOCAL_BASE_PATH=/data/uploads \
-v $(pwd)/picstr-data:/data \
ghcr.io/moser-systems/picstr:latest
```

#### With MariaDB and local storage:

```bash
podman run -p 8080:8080 \
  -e DB_URL="jdbc:mariadb://db:3306/picstr?createDatabaseIfNotExist=true" \
  -e DB_USER=picstr \
  -e DB_PASSWORD=secret \
  -e APP_STORAGE_TYPE=local \
  -e APP_STORAGE_LOCAL_BASE_PATH=/data/uploads \
  -v /your/uploads:/data/uploads \
  ghcr.io/moser-systems/picstr:latest
```

---

## Configuration Reference

All values can be provided via environment variables or as Spring Boot properties.

### Database

#### MariaDB / MySQL

Properties:

```properties
spring.datasource.url=${DB_URL:jdbc:mariadb://localhost:3306/picstr?createDatabaseIfNotExist=true}
spring.datasource.username=${DB_USER:picstr}
spring.datasource.password=${DB_PASSWORD:picstr}
```

ENV vars:

```bash
DB_URL=jdbc:mariadb://db:3306/picstr?createDatabaseIfNotExist=true
DB_USER=picstr
DB_PASSWORD=secret
```

#### PostgreSQL

Properties:

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:5432/picstr}
spring.datasource.username=${DB_USER:picstr}
spring.datasource.password=${DB_PASSWORD:picstr}
```

ENV vars:

```bash
DB_URL=jdbc:postgresql://db:5432/picstr
DB_USER=picstr
DB_PASSWORD=secret
```

#### H2 (dev / CI / testing)

Properties:

```properties
spring.datasource.url=jdbc:h2:file:./data/picstr-dev;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH
spring.datasource.username=sa
spring.datasource.password=
```

---

### Storage

#### S3 / MinIO

```properties
app.storage.type=s3
app.storage.s3.endpoint=${APP_STORAGE_S3_ENDPOINT:http://localhost:9000}
app.storage.s3.region=${APP_STORAGE_S3_REGION:eu-central-1}
app.storage.s3.bucket=${APP_STORAGE_S3_BUCKET:picstr-images}
app.storage.s3.access-key=${APP_STORAGE_S3_ACCESS_KEY:minioadmin}
app.storage.s3.secret-key=${APP_STORAGE_S3_SECRET_KEY:minioadmin}
app.storage.s3.path-style-access-enabled=${APP_STORAGE_S3_PATH_STYLE_ACCESS_ENABLED:true}
```

```bash
APP_STORAGE_TYPE=s3
APP_STORAGE_S3_ENDPOINT=https://s3.eu-central-1.amazonaws.com  # AWS; or MinIO URL
APP_STORAGE_S3_REGION=eu-central-1
APP_STORAGE_S3_BUCKET=my-picstr-bucket
APP_STORAGE_S3_ACCESS_KEY=AKIAIOSFODNN7EXAMPLE
APP_STORAGE_S3_SECRET_KEY=wJalrXUtnFEMI/K7MDENG/bPxRfiCYEXAMPLEKEY
APP_STORAGE_S3_PATH_STYLE_ACCESS_ENABLED=false  # false for AWS, true for MinIO
```

#### Local filesystem

```bash
APP_STORAGE_TYPE=local
APP_STORAGE_LOCAL_BASE_PATH=/var/data/picstr/uploads
```

#### FTP

```bash
APP_STORAGE_TYPE=ftp
APP_STORAGE_FTP_HOST=ftp.example.com
APP_STORAGE_FTP_PORT=21
APP_STORAGE_FTP_USERNAME=ftpuser
APP_STORAGE_FTP_PASSWORD=ftppass
APP_STORAGE_FTP_BASE_PATH=/picstr/uploads
```

---

### File upload size limits

```properties
spring.servlet.multipart.max-file-size=20MB
spring.servlet.multipart.max-request-size=20MB
```

---

### Background jobs

#### Archive purge

Permanently removes archived photos (records + storage files) older than the retention window.

```bash
APP_PHOTO_ARCHIVE_RETENTION_DAYS=30          # keep archived photos for 30 days (default)
APP_PHOTO_ARCHIVE_PURGE_CRON=0 0 3 * * *    # run at 3 AM daily (default)
```

#### Missing files detection

Archives DB records whose physical files (original or thumbnail) no longer exist in storage.

```bash
APP_PHOTO_MISSING_FILES_DETECTION_ENABLED=true
APP_PHOTO_MISSING_FILES_DETECTION_CRON=0 0 4 * * *  # run at 4 AM daily (default)
```

#### Storage reconciliation

Iterates all storage keys; creates missing DB records and generates missing thumbnails.

```bash
APP_PHOTO_RECONCILE_ENABLED=true
APP_PHOTO_RECONCILE_CRON=0 15 4 * * *  # run at 4:15 AM daily (default)
```

> To disable any job without redeploying, set its `*_ENABLED=false` environment variable.

---

## Authentication (OAuth2 / OpenID Connect)

PicStr supports OAuth2 and OpenID Connect (OIDC) authentication, allowing integration with identity providers like Microsoft Entra ID, Google, and others.

### Authentication Modes

Use `app.security.auth-mode` (or `APP_SECURITY_AUTH_MODE`) to choose authentication behavior explicitly:

```properties
app.security.auth-mode=basic   # HTTP Basic auth
app.security.auth-mode=oauth2  # OpenID Connect / OAuth2 login
app.security.auth-mode=none    # Disable authentication entirely
```

### Disabling Authentication (Development/Testing)

To disable authentication entirely (useful for development or testing), set:

```properties
app.security.auth-mode=none
```

Or as an environment variable:
```bash
APP_SECURITY_AUTH_MODE=none
```

When disabled, all endpoints will be publicly accessible without requiring login. **Only use this in development environments.**

Example for local development:
```bash
./mvnw spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--app.security.auth-mode=none"
```

---

## Example docker-compose

```yaml
services:
  picstr:
    image: ghcr.io/moser-systems/picstr:latest
    build:
      context: .
      dockerfile: Dockerfile.multistage
    ports:
      - "8080:8080"
    environment:
      DB_URL: jdbc:mariadb://db:3306/picstr?createDatabaseIfNotExist=true
      DB_USER: picstr
      DB_PASSWORD: secret
      APP_STORAGE_TYPE: s3
      APP_STORAGE_S3_ENDPOINT: http://minio:9000
      APP_STORAGE_S3_BUCKET: picstr-images
      APP_STORAGE_S3_ACCESS_KEY: minioadmin
      APP_STORAGE_S3_SECRET_KEY: minioadmin
      APP_STORAGE_S3_PATH_STYLE_ACCESS_ENABLED: "true"
    depends_on:
      - db
      - minio

  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: picstr
      MARIADB_USER: picstr
      MARIADB_PASSWORD: secret
      MARIADB_ROOT_PASSWORD: root
    volumes:
      - db-data:/var/lib/mysql

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio-data:/data
    ports:
      - "9000:9000"
      - "9001:9001"

volumes:
  db-data:
  minio-data:
```

---

## Example docker-compose with OIDC and Microsoft Entra ID

```yaml
services:
  picstr:
    image: ghcr.io/moser-systems/picstr:latest
    build:
      context: .
      dockerfile: Dockerfile.multistage
    ports:
      - "8080:8080"
    environment:
      # Spring Profile for OIDC
      SPRING_PROFILES_ACTIVE: oidc-microsoft
      
      # OAuth2 / OpenID Connect Configuration (Microsoft Entra ID)
      OIDC_CLIENT_ID: ${OIDC_CLIENT_ID}           # From: App registrations > Application (client) ID
      OIDC_CLIENT_SECRET: ${OIDC_CLIENT_SECRET}   # From: Certificates & secrets
      OIDC_TENANT_ID: ${OIDC_TENANT_ID:common}    # Your tenant ID (optional, defaults to common)
      
      # Database Configuration
      DB_URL: jdbc:mariadb://db:3306/picstr?createDatabaseIfNotExist=true
      DB_USER: picstr
      DB_PASSWORD: picstr-secret
      
      # Storage Configuration (S3 / MinIO)
      APP_STORAGE_TYPE: s3
      APP_STORAGE_S3_ENDPOINT: http://minio:9000
      APP_STORAGE_S3_BUCKET: picstr-images
      APP_STORAGE_S3_ACCESS_KEY: minioadmin
      APP_STORAGE_S3_SECRET_KEY: minioadmin
      APP_STORAGE_S3_PATH_STYLE_ACCESS_ENABLED: "true"
    depends_on:
      - db
      - minio

  db:
    image: mariadb:11
    environment:
      MARIADB_DATABASE: picstr
      MARIADB_USER: picstr
      MARIADB_PASSWORD: picstr-secret
      MARIADB_ROOT_PASSWORD: root-secret
    volumes:
      - db-data:/var/lib/mysql

  minio:
    image: minio/minio
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    volumes:
      - minio-data:/data
    ports:
      - "9000:9000"
      - "9001:9001"

volumes:
  db-data:
  minio-data:
```

**To use this configuration:**

1. Create a `.env` file in the same directory as `docker-compose.yml`:
```env
OIDC_CLIENT_ID=your-microsoft-client-id
OIDC_CLIENT_SECRET=your-microsoft-client-secret
OIDC_TENANT_ID=your-tenant-id
```

2. Run the docker-compose:
```bash
docker-compose up
```

3. Access PicStr at `http://localhost:8080` and authenticate with Microsoft Entra ID

---

## Development Tips

- Use the `dev` Spring profile for local runs — it enables local storage, relaxed scheduling (jobs run every second) and short archive retention.
- Pass `--logging.level.io.picstr=DEBUG` for verbose application logs.
- The H2 console is not enabled by default; add `spring.h2.console.enabled=true` and `spring.h2.console.path=/h2-console` to a dev-specific properties file when needed.
- To disable a scheduled job temporarily without redeploying, set the corresponding `*_ENABLED=false` environment variable.

## Contributing 

Contributions, bug reports and feature requests are welcome! Please open an issue or submit a pull request on GitHub.

## License
This project is licensed under the MIT License. See the [LICENSE](LICENSE) file for details.

## Future Enhancements
- Bulk upload and management features
- Search functionality by filename, description, category, tags, and GPS coordinates
- API endpoints for integration with other applications or mobile clients
