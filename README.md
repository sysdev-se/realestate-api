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

## API

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/v1/auth/signin` | Public. `{"email","password"}` → `{"accessToken","refreshToken"}` |
| `POST` | `/api/v1/auth/refresh` | Public. `{"refreshToken"}` → `{"accessToken"}` |
| `GET` | `/api/v1/consumer/listings` | Public. `?title=` (contains, case-insensitive), `page`, `size` (max 100), `sort=title\|price\|id[,asc\|desc]` |
| `GET` | `/api/v1/consumer/listings/{id}` | Public |
| `POST` | `/api/v1/admin/listings` | `Authorization: Bearer <access token>` with role ADMIN |

Errors are returned as problem details (`application/problem+json`).

```bash
TOKEN=$(curl -s localhost:8080/api/v1/auth/signin -H 'Content-Type: application/json' \
  -d '{"email":"rikard.virta@sysdev.se","password":"HelloWorld@123"}' | jq -r .accessToken)
curl -s 'localhost:8080/api/v1/consumer/listings?title=villa&sort=price,desc' | jq
curl -si localhost:8080/api/v1/admin/listings -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' -d '{"title":"New","price":1000000,"imgUrls":[]}'
```

### Configuration

| Env var | Default | |
|---|---|---|
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated browser origins allowed to call `/api/**` (CORS). Postman/curl don't use CORS and always work |
| `ADMIN_PASSWORD` | none | Password of the seeded admin. Unset = no admin user |
| `ACCESS_TOKEN_SECRET` | `TEMPORARY_ACCESS_TOKEN_SECRET_CHANGE_ME` | HS256 key for access tokens (15 min), ≥ 32 bytes |
| `REFRESH_TOKEN_SECRET` | `TEMPORARY_REFRESH_TOKEN_SECRET_CHANGE_ME` | HS256 key for refresh tokens (7 days), ≥ 32 bytes, must differ |

The JWT defaults are public and only meant for local runs. On Cloud Run all three come from
Secret Manager (realestate-infrastructure `environments/prod/secrets.tf`). The prod admin password:
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
