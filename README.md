# realestate-api

REST API for the real estate listing site. Spring Boot 4.1, Java 25, built as a GraalVM native
image and deployed to Cloud Run (`realestate-api`, project `realestate-prod-000001`,
`asia-southeast1`). The service itself is provisioned by `realestate-infrastructure`.

## Develop

Requires JDK 25 (`sdk install java 25-graalce` gives you both the JDK and `native-image`).

```bash
./mvnw verify                 # tests
ADMIN_PASSWORD=HelloWorld@123 ./mvnw spring-boot:run   # http://localhost:8080/hello
```

Data lives in an in-memory H2 database (Spring Data JPA) that is recreated on every start and
seeded with three sample listings and, if `ADMIN_PASSWORD` is set, an admin user
`rikard.virta@sysdev.se` with that password (without it no admin is created).

## Listings

| Field | |
|---|---|
| `publishedStatus` | `PUBLISHED` (public), `PENDING` (scraped, waiting for an admin) or `REJECTED` (kept so the scraper doesn't add it again; images deleted) |
| `type` | `SALE` or `RENT` |
| `price` | Whole VND; per month for `RENT`. The system's only currency |
| `bedrooms`, `bathrooms`, `areaSqm` | Optional |
| `source`, `sourceUrl` | The scraper (e.g. `fazwaz`) and the listing's page on that website; null for admin-created listings |
| `imgUrls`, `primaryImgUrl` | Ordered; the first is the primary image (null without images) |

Admin-created listings are published right away; scraped ones start as `PENDING`.

## API

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/v1/auth/signin` | Public. `{"email","password"}` → `{"accessToken","refreshToken"}` |
| `POST` | `/api/v1/auth/refresh` | Public. `{"refreshToken"}` → `{"accessToken"}` |
| `GET` | `/api/v1/consumer/listings` | Public, published only. `?title=` (contains, case-insensitive), `page`, `size` (max 100), `sort=title\|price\|id[,asc\|desc]` |
| `GET` | `/api/v1/consumer/listings/{id}` | Public, published only (others are 404) |
| `GET` | `/api/v1/consumer/images/{name}` | Public. The image bytes, cached by browsers for a year |
| `GET` | `/api/v1/admin/listings?publishedStatus=PENDING` | Admin. Listings in one status; same `title`, paging and sorting as the public list |
| `GET` | `/api/v1/admin/listings/{id}` | Admin. A listing in any status |
| `POST` | `/api/v1/admin/listings` | Admin (`Authorization: Bearer <access token>` with role ADMIN). Published right away |
| `POST` | `/api/v1/admin/listings/{id}/publish` | Admin. `PENDING` → `PUBLISHED`; 409 in any other status. Optional body `{"imgUrls": [...]}` (from `POST /admin/images`, first = cover) replaces its images; the ones it no longer has are deleted |
| `POST` | `/api/v1/admin/listings/{id}/reject` | Admin. `PENDING` → `REJECTED`, deletes its images; 409 in any other status |
| `DELETE` | `/api/v1/admin/listings/{id}` | Admin. 204; also deletes the listing's uploaded images |
| `POST` | `/api/v1/admin/images` | Admin. Multipart `file` (JPEG, PNG or WebP, ≤ 10 MB) → 201 `{"url"}` |
| `POST` | `/api/v1/scraper/listings` | Scrapers. Multipart: `listing` (JSON, see below) + up to 20 `images` → 201 `{"status":"CREATED","listing"}` or 200 `{"status":"SKIPPED"}` |
| `POST` | `/api/v1/scraper/listings/unknown-ids` | Scrapers. `{"sourceIds":[…≤100]}` → `{"sourceIds":[…]}`, the ones the API doesn't have yet |

To add a listing with your own pictures, upload each image, then create the listing with the
returned URLs.

Errors are returned as problem details (`application/problem+json`).

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/signin -H 'Content-Type: application/json' \
  -d '{"email":"rikard.virta@sysdev.se","password":"HelloWorld@123"}' | jq -r .accessToken)
curl -s 'localhost:8080/api/v1/consumer/listings?title=villa&sort=price,desc' | jq
curl -si localhost:8080/api/v1/admin/listings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"type":"SALE","title":"New","price":3000000000,"imgUrls":[]}'
curl -s localhost:8080/api/v1/admin/images -H "Authorization: Bearer $TOKEN" -F file=@photo.jpg
curl -s 'localhost:8080/api/v1/admin/listings?publishedStatus=PENDING' -H "Authorization: Bearer $TOKEN" | jq
```

### Scrapers

Each scraper (its own repo and Cloud Run job, e.g. `realestate-scraper-fazwaz`) calls
`/api/v1/scraper/**` with a Google ID token of its Cloud Run service account:

- audience = this API's URL (`SCRAPER_AUDIENCE`),
- from the metadata server with `format=full`, so it carries `email` and `email_verified`:
  `http://metadata.google.internal/computeMetadata/v1/instance/service-accounts/default/identity?audience=<api url>&format=full`,
- the email must be in `SCRAPER_ACCOUNTS`, which also names its source.

Nothing else gets in there (admin tokens included), and scraper tokens work nowhere else. The
source comes from the token, so a scraper can't write another source's listings. A listing is
stored once per `(source, sourceId)`: later sends are skipped in any status, so rejected
listings stay out. `listing` part:

```json
{"sourceId":"6922192","sourceUrl":"https://www.fazwaz.vn/property-rent/...-u6922192","type":"RENT",
 "title":"6 Bedroom Villa","description":"…","price":45000000,"bedrooms":6,"bathrooms":6,"areaSqm":500}
```

Images are stored like admin uploads (JPEG, PNG or WebP, ≤ 10 MB each, ≤ 30 MB per request); if
the listing can't be stored they are deleted again.

### Configuration

| Env var | Default | |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins allowed to call `/api/**` (CORS). Postman/curl don't use CORS and always work |
| `IMAGE_BUCKET` | none | Cloud Storage bucket for uploaded images. Unset = kept in memory, lost on restart |
| `ADMIN_PASSWORD` | none | Password of the seeded admin. Unset = no admin user |
| `ACCESS_TOKEN_SECRET` | `TEMPORARY_ACCESS_TOKEN_SECRET_CHANGE_ME` | HS256 key for access tokens (15 min), ≥ 32 bytes |
| `REFRESH_TOKEN_SECRET` | `TEMPORARY_REFRESH_TOKEN_SECRET_CHANGE_ME` | HS256 key for refresh tokens (7 days), ≥ 32 bytes, must differ |
| `SCRAPER_AUDIENCE` | none | Audience scrapers' ID tokens must have (the API's URL). Unset = scrapers rejected |
| `SCRAPER_ACCOUNTS` | none | Comma-separated `source=service account email` pairs allowed to call `/api/v1/scraper/**`. Unset = scrapers rejected |

The JWT defaults are public and only meant for local runs. On Cloud Run all three come from
Secret Manager (realestate-infrastructure `environments/prod/secrets.tf`). The bucket is private and
the app reaches it as its Cloud Run service account (token from the metadata server), so
`IMAGE_BUCKET` only works on Cloud Run. The prod admin password:
`gcloud secrets versions access latest --secret realestate-api-admin-password --project realestate-prod-000001`.

Native image as a container (needs Docker, not a local GraalVM):

```bash
./mvnw -Pnative spring-boot:build-image -Dspring-boot.build-image.imageName=realestate-api:local
docker run --rm -p 8080:8080 realestate-api:local
```

## CI/CD

`.github/workflows/ci.yml`:
- PR to `main`: `./mvnw verify`.
- Push to `main`: verify, build the native image, push it to Artifact Registry and
  `gcloud run deploy --image` (only the image; Terraform owns all other service settings).

Repo secrets: `GCP_WIF_PROVIDER`, `GCP_SERVICE_ACCOUNT`.
