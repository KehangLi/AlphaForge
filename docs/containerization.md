# Backend container image

Build and run the backend from the repository root with Docker Desktop (or another
Docker Engine with Buildx). Building the image does not require Java or Maven on
the host, database access, or production secrets.

## Build

```sh
./scripts/build-image.sh
```

This builds `alphaforge-backend:local` for the Docker builder's native platform
and loads the result into the local Docker image store. The script also works
when invoked from another directory. Use `--help` to see its arguments.

For Azure Container Apps, build a Linux AMD64 image:

```sh
./scripts/build-image.sh --platform linux/amd64 --tag alphaforge-backend:0.1.0
```

Azure Container Apps requires `linux/amd64`. An Apple Silicon Mac normally builds
`linux/arm64`; do not deploy that local image to Azure. Each command builds and
loads one platform. Docker Desktop can run the AMD64 image locally using emulation;
a remote builder may need additional platform support.

The Dockerfile has two stages:

1. The build stage uses Java 21 and the repository's Maven Wrapper to compile and
   package the application. Maven dependencies and the wrapper distribution are
   cached with a BuildKit cache mount. Java bytecode is built on the builder's
   native architecture, so an AMD64 runtime image does not require emulating the
   compilation on Apple Silicon. Revisit this if native compilation or
   architecture-specific build outputs are introduced.
2. The runtime stage contains the Java 21 JRE and the executable application JAR.
   It runs Java directly as UID/GID `10001:10001`, exposes internal port `8080`,
   and defaults to `SPRING_PROFILES_ACTIVE=prod`. Signals reach Java directly so
   that `docker stop` can shut the application down normally.

Both Temurin base images are pinned to multi-platform manifest digests. Update
the digests deliberately to receive later Java and operating-system fixes, then
rebuild and rescan. A cached rebuild alone does not update a pinned base image.

`.dockerignore` allows only backend build inputs. It excludes frontend files,
Git history, local `target/`, tests, scripts, documentation, and environment files.
The final stage copies only the JAR. Do not add secrets to source resources,
Dockerfile `ARG`/`ENV`, Maven settings in the context, or build command arguments.

## Tests are separate

```sh
./mvnw test
```

Tests require Java 21 and Docker on the host, as described in [testing.md](testing.md).
The image build uses `-Dmaven.test.skip=true` and does not copy test sources. It
compiles and packages production code without running or compiling tests. A
successful build is not proof that tests passed; CI should run both steps.

## Run against a disposable local database

```sh
# 1. build the image
./scripts/build-image.sh

# 2. start server and the temporary DB
docker compose -f compose.container.yml up -d --wait

# 3. check the server
curl -i http://localhost:18080/health

# 4. end the validation，delete the temporary container
docker compose -f compose.container.yml down
```

The Compose file uses a separate project named `alphaforge-image-smoke`, a private
network, and a PostgreSQL database stored in temporary memory. It never mounts
the development `pgdata` volume and does not publish the database port. Its
hard-coded database credentials are disposable local examples, not production
credentials. The backend port is available only on localhost.

The backend explicitly uses `local` for this check because the disposable database
has no TLS and the local endpoint has no HTTPS. Development data seeding is
disabled. This verifies the packaged application, Flyway migrations, database
connectivity, and HTTP startup; it does not validate production database TLS or
browser authentication. `/health` is a basic process check, not a continuous
database readiness probe.

To run a different image or use a different host port:

```sh
BACKEND_IMAGE=alphaforge-backend:0.1.0 BACKEND_PORT=18081 \
  docker compose -f compose.container.yml up -d --wait
```

View logs and stop the temporary environment:

```sh
docker compose -f compose.container.yml logs backend
docker compose -f compose.container.yml down
```

Removing the containers discards this temporary database. Use the same image/port
variable overrides when managing an environment started with overrides. Leave
the existing `compose.yml` for ordinary development.

## Production configuration

The image's default `prod` profile checks required configuration before opening
database connections. Running it without database configuration should exit with
an error naming `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD`:

```sh
docker run --rm --network none alphaforge-backend:local
```

At deployment, provide the database address and username as environment variables
and `DB_PASSWORD` through an Azure Container Apps Secret reference. The database
must support the production profile's TLS and certificate/hostname verification.
The listening port is `8080` unless `SERVER_PORT` is supplied at runtime; configure
Azure ingress/probes to match and use HTTPS at the public ingress.

Production credentials are supplied only at runtime. The packaged local profile
still contains its disposable development defaults. See
[production-configuration.md](production-configuration.md) for required variables,
certificate trust, secret references, and password rotation. Frontend proxy,
Cookie, and CSRF integration are verified during frontend deployment.

## Scan the finished image

For example, use the same pinned Trivy version as the security workflow. Export
the local image so the scanner does not need the Docker socket:

```sh
mkdir -p /tmp/alphaforge-image-scan
docker image save --output /tmp/alphaforge-image-scan/backend.tar alphaforge-backend:local
docker run --rm \
  --mount type=bind,source=/tmp/alphaforge-image-scan,target=/scan \
  aquasec/trivy:0.76.1 image \
  --input /scan/backend.tar --cache-dir /scan/trivy-cache --scanners vuln \
  --severity HIGH,CRITICAL --exit-code 1 --timeout 20m
```

This scans both OS packages and the Java libraries in the image. The first run
downloads vulnerability databases. A clean dependency/JAR scan does not guarantee
a clean container image; the JRE and OS add their own packages. Resolve or assess
findings before release. See [security-checks.md](security-checks.md) for the
existing severity policy. The build script only loads a local image. For the
manual versioned GHCR/Azure release and rollback workflows, see
[backend-release.md](backend-release.md).

## References

- [Docker multi-stage builds](https://docs.docker.com/build/building/multi-stage/)
- [Docker build cache](https://docs.docker.com/build/cache/optimize/)
- [Temurin official images](https://hub.docker.com/_/eclipse-temurin)
- [Container Apps image requirements](https://learn.microsoft.com/en-us/azure/container-apps/containers)
