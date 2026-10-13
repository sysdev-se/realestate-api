# realestate-api

REST API for the real estate listing site. Spring Boot 4.1, Java 25, built as a GraalVM native
image and deployed to Cloud Run (`realestate-api`, project `realestate-prod-000001`,
`asia-southeast1`). The service itself is provisioned by `realestate-infrastructure`.

## Develop

Requires JDK 25 (`sdk install java 25-graalce` gives you both the JDK and `native-image`).

```bash
./mvnw verify                 # tests
./mvnw spring-boot:run        # http://localhost:8080/hello
```

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
