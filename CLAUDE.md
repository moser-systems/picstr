# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

PicStr is a mobile-first, self-hosted photo upload/management app: Java 25, Spring Boot 4, Spring Data JPA, Flyway, Thymeleaf (+ Layout Dialect), Tabler UI with htmx/hyperscript, Lombok. Photos are uploaded straight to a storage backend (never kept on the device) and organised by one category plus many tags. User management, multi-tenancy, AI features and photo editing are explicit non-goals.

## Commands

```bash
./mvnw clean package                 # full build; frontend-maven-plugin installs Node and runs `npm install` + `npm run build`
./mvnw clean package -DskipTests
./mvnw test                          # also produces JaCoCo report in target/site/jacoco (CI enforces 40% overall / 60% changed files)
./mvnw test -Dtest=PhotoServiceTest                  # single test class
./mvnw test -Dtest=PhotoServiceTest#purgeArchivedOlderThanDays_returnsZeroWhenNoCandidates
npm run build                        # copy vendor JS/CSS from node_modules into src/main/resources/static/vendor (build.mjs)
./mvnw versions:display-dependency-updates
```

Run locally with H2 + local storage, no auth:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.arguments="--spring.datasource.url=jdbc:h2:file:./data/picstr-dev;MODE=MariaDB;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH --spring.datasource.username=sa --spring.datasource.password= --app.storage.type=local --app.storage.local.base-path=./data/uploads"
```

Note: the `dev` profile sets `auth-mode=none`, `storage.type=local`, and runs all scheduled jobs **every second** (cron `* * * * * *`) with a 1-day archive retention. Without a datasource override it connects to MariaDB with `root/root`.

Thumbnail generation shells out to GraphicsMagick (`gm`, searched in `app.thumbnail.gm.search-path`, default `/usr/bin`), so it must be installed on the host for uploads to produce thumbnails.

## Architecture

Package root: `io.picstr.app` — `controller`, `service`, `repository`, `model`, `config`. Controllers use server-rendered Thymeleaf views in `src/main/resources/templates/{photo,category,tag}/` with `layout.html` as the shared layout; view controllers extend `BaseController` (adds `currentUrl` and `activeProfile` model attributes).

**Pluggable beans selected by property** (`@ConditionalOnProperty`), not by profile:
- `StorageService` (upload/get/exists/listKeys/delete over flat string keys; use `exists` rather than `get` when only presence matters — jobs rely on it to avoid downloads) → `S3StorageService` (+ `S3Config`), `LocalStorageService`, `FtpStorageService`, chosen by `app.storage.type` (`s3` default, `local`, `ftp`).
- `ThumbnailService` → `GraphicsMagickThumbnailService` via `app.thumbnail.engine=graphicsmagick`.

**Storage key convention** (relied on by several services): originals are stored as `<UUID><ext>` (= `Photo.internalFilename`); thumbnails are JPEG and stored in the same backend under `ThumbnailKeys.forOriginal(key)`: `thumb_<internalFilename>` for `.jpg` originals, `thumb_<internalFilename>.jpg` otherwise. Always go through `ThumbnailKeys` (or `photo.thumbnailKey` in templates) instead of concatenating `thumb_`; `ThumbnailKeyMigration` moves thumbnails stored under the old `thumb_<internalFilename>` key at startup. Any key not starting with `thumb_` is treated as an original. Files are served only via `PhotoAssetController` at `/assets/{key}`, for every backend. `/assets/**` is public even when auth is on, except files of archived photos, which `ArchivedAssetAuthorizationManager` (wired in `SecurityConfig`) restricts to logged-in users. `CurrentUserAdvice` exposes `currentUser` to every view in `oauth2` mode only; the layout shows the sign-out button when it is set.

**Upload flow** (`PhotoService.upload`): validate `image/*` → extract EXIF GPS (metadata-extractor) into lat/long → convert HEIC/HEIF to JPEG (`HeicHeifConversionService`) → store original → create thumbnail → persist `Photo`. Categories and tags are resolved/created by name.

**Soft delete**: archiving sets `Photo.deleteDate`; public-facing repository methods carry a `DeleteDateIsNull` condition (archive views use `DeleteDateIsNotNull`), so new queries must follow the same pattern. Three scheduled jobs in `service/` (each enable/cron-configurable under `app.photo.*`):
- `ArchivedPhotoPurgeJob` — hard-deletes archived photos (DB + storage) past `archive.retention-days`.
- `MissingStorageFilesDetectionJob` — archives photos whose original or thumbnail is missing from storage.
- `StoragePhotoReconciliationJob` — walks `listKeys()`, creates `Photo` rows for orphan originals and regenerates missing thumbnails.

**Security** (`SecurityConfig`): `app.security.auth-mode` = `basic` (default), `oauth2` (requires `spring.security.oauth2.client.registration.*`; see `application-oidc-microsoft.properties`), or `none`. CSRF protection is on (except in `none` mode): always build forms with `th:action` so the token is added, and new htmx `hx-post`/etc. requests pick it up from the `_csrf` meta tags in `layout.html`. `/assets/**`, `/vendor/**` and `/actuator/health` (the only exposed Actuator endpoint) are public.

**Database**: `ddl-auto=none`; schema is owned by Flyway with per-vendor migrations in `src/main/resources/db/migration/{h2,mariadb,postgresql}/` (history table `migrations`). Any schema change needs a migration in **all three** vendor directories. Tests use in-memory H2 in MariaDB mode with `ddl-auto=validate`, so entities must match the H2 migration.

**i18n**: UI strings live in `messages*.properties` (en default, de, fr, it, es, pt, ja, zh); new keys should be added to every locale file. Locale switches via `?lang=` (`WebLangConfig`).

**RSS**: `FeedController` serves recent photos at `/feed/recent.xml` (ROME).

## Tests

Tests are plain JUnit 5 + Mockito unit tests (`@ExtendWith(MockitoExtension.class)`), not Spring context tests. Controllers are instantiated directly and field-injected dependencies are set with `ReflectionTestUtils.setField`.

## Frontend assets

Vendor assets (Tabler, tom-select, Leaflet, htmx, hyperscript, Inter font) come from npm and are copied into `src/main/resources/static/vendor/` by `build.mjs`; the copies are committed, so re-run `npm run build` and commit the result after changing `package.json`. Pages must not load anything from third-party hosts (map tiles excepted). Templates use Tabler 1.6 class names (`.form-text`, `.badge-list`); dark mode comes from `tabler-theme.js`, so use Tabler colour tokens instead of hard-coded colours. View helpers live in `io.picstr.app.view` (e.g. `${@fmt.bytes(...)}`).

See `docs/SPEC.md` for requirement IDs (`FR-*`, `NFR-*`) and the known-issues list.
