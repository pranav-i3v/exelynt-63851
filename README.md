# Resource Booking System

A RESTful booking system for rooms, vehicles and equipment, built as **one Spring
Boot application** (a modular monolith — no microservices). Stateless JWT
authentication with refresh-token rotation, role-based authorisation, filtered and
paginated queries via JPA Specifications, structured JSON logging with correlation
IDs, and a full audit trail.

---

## Table of contents

1. [Tech stack](#tech-stack)
2. [Prerequisites](#prerequisites)
3. [Environment variables](#environment-variables)
4. [JWT signing keys](#jwt-signing-keys)
5. [Database setup](#database-setup)
6. [Build and run](#build-and-run)
7. [Seed users (test only)](#seed-users-test-only)
8. [Swagger / OpenAPI](#swagger--openapi)
9. [Token flow with curl](#token-flow-with-curl)
10. [API reference](#api-reference)
11. [Refresh tokens and session revocation](#refresh-tokens-and-session-revocation)
12. [Error shape](#error-shape)
13. [Health endpoints](#health-endpoints)
14. [Logging and the correlation ID](#logging-and-the-correlation-id)
15. [Audit trail](#audit-trail)
16. [Tests](#tests)
17. [Project layout](#project-layout)
18. [Security notes](#security-notes)

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / runtime | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Security | Spring Security 7.1.1 (the version Boot 4.1.1 manages), stateless JWT RS256 |
| Persistence | Spring Data JPA / Hibernate 7 |
| Validation | Jakarta Bean Validation (Hibernate Validator) |
| Database | PostgreSQL by default, MySQL through configuration only |
| API docs | springdoc-openapi 3.1.1 (Swagger UI with Bearer auth) |
| Secrets | AWS Secrets Manager (AWS SDK v2) for the RSA signing key |
| Ops | Spring Boot Actuator (health + info only) |
| Tests | JUnit 5, Mockito, MockMvc, Spring Security Test, H2 |

> **Note on Spring Security 6.** The brief asked for Spring Security 6, but Boot
> 4.1.1 manages Spring Security 7.1.1 and the two are not interchangeable (Boot 4
> builds on Spring Framework 7). Pinning Security 6 under Boot 4 does not compile,
> so the project uses the managed 7.1.1. Every requirement — stateless JWT filter,
> method-level RBAC, custom entry point and access-denied handler — is implemented
> with the same API shape you would use on Security 6.

---

## Prerequisites

* JDK 21 (`java -version` should report 21)
* Maven 3.9+ (or use the `mvnw` wrapper if you add one)
* PostgreSQL 14+ **or** MySQL 8+
* `curl` and `psql` / `mysql` clients for the examples below

---

## Environment variables

Every secret is read from the environment. `application.yml` uses `${VAR}`
placeholders **with no default for any secret**, so a missing variable fails
startup rather than falling back to something weak. Copy `.env.example` to `.env`
and fill it in; `.env` is git-ignored and must never be committed.

| Variable | Required | Default | Description |
|---|---|---|---|
| `DB_URL` | yes | — | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/booking_db` |
| `DB_USERNAME` | yes | — | Database user |
| `DB_PASSWORD` | yes | — | Database password |
| `JWT_SECRET_ID` | yes | — | Name or ARN of the AWS Secrets Manager secret holding the RSA key pair |
| `AWS_REGION` | no | AWS chain | Region of the secret; falls back to the standard AWS region chain |
| `JWT_KEY_REFRESH_MINUTES` | no | `0` | Minutes between re-reads of the secret; `0` disables refresh |
| `JWT_ACCESS_EXPIRY_MINUTES` | no | `15` | Access-token lifetime in minutes |
| `JWT_REFRESH_EXPIRY_DAYS` | no | `7` | Refresh-token lifetime in days |
| `TOKEN_BLACKLIST` | no | `in-memory` | `in-memory` (single instance) or `redis` (required for more than one replica) |
| `REDIS_HOST` / `REDIS_PORT` | when blacklist is `redis` | `localhost` / `6379` | Redis endpoint |
| `REDIS_USERNAME` / `REDIS_PASSWORD` | when Redis needs auth | — | Redis credentials |
| `REDIS_SSL` | no | `false` | TLS to Redis |
| `REFRESH_TOKEN_PURGE_CRON` | no | `0 15 * * * *` | When to sweep expired refresh tokens |
| `SPRING_PROFILES_ACTIVE` | no | — | Set to `mysql` to run against MySQL |
| `SERVER_PORT` | no | `8080` | HTTP port |
| `DB_POOL_SIZE` | no | `10` | Hikari maximum pool size |
| `DDL_AUTO` | no | `validate` | Hibernate schema handling; the schema itself comes from `db/<engine>/02_schema.sql` |

**No variable in this table carries key material.** The RSA key pair is read
from AWS Secrets Manager and nowhere else; `JWT_SECRET_ID` only names the
secret. AWS credentials are likewise never read from configuration — they come
from the standard AWS provider chain, an IAM role in production.

---

## JWT signing keys

Access tokens are signed with **RS256**: the application signs with an RSA
private key, and anything that needs to verify a token only needs the public
key. No shared secret is involved.

### AWS Secrets Manager is the only source

There is no second way to supply a key. The application will not read key
material from a property, an environment variable or a file, and it never
generates a key pair of its own — `RsaKeys` has no key-generation method at all.
If the secret cannot be read, the application does not start.

The key id from the secret is published as the JWT `kid` header, and the key
material is validated at startup: a missing secret, an unreadable secret, a
PKCS#1 or passphrase-encrypted key, or an RSA key below 2048 bits each abort the
boot with a message naming the problem.

### Creating the key pair

```bash
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out jwt-private.pem
openssl rsa -in jwt-private.pem -pubout -out jwt-public.pem
```

The private key must be PKCS#8 (`BEGIN PRIVATE KEY`). If you have a PKCS#1 key
(`BEGIN RSA PRIVATE KEY`), convert it once - the application says so explicitly
if you forget:

```bash
openssl pkcs8 -topk8 -nocrypt -in jwt-private.pem -out jwt-private-pkcs8.pem
```

### Storing it in AWS Secrets Manager

The secret value may be a bare PEM private key, or JSON - which is what lets you
pin an explicit `kid`:

```json
{
  "privateKey": "-----BEGIN PRIVATE KEY-----\n...\n-----END PRIVATE KEY-----",
  "publicKey":  "-----BEGIN PUBLIC KEY-----\n...\n-----END PUBLIC KEY-----",
  "keyId":      "booking-2026-09"
}
```

`publicKey` is optional - it is derived from the private key when absent - and
`keyId` falls back to a stable fingerprint of the public key.

```bash
# Build the secret from the generated key pair, then destroy the local private key
jq -n --rawfile priv jwt-private.pem --rawfile pub jwt-public.pem \
   '{privateKey:$priv, publicKey:$pub, keyId:"booking-2026-09"}' > /tmp/jwt-secret.json

aws secretsmanager create-secret \
  --name booking/jwt-signing-key \
  --description "RS256 signing key for the Resource Booking System" \
  --secret-string file:///tmp/jwt-secret.json

rm /tmp/jwt-secret.json jwt-private.pem
```

Point the application at it:

```bash
export JWT_SECRET_ID=booking/jwt-signing-key
export AWS_REGION=eu-west-1
```

### IAM permissions

The application only ever reads one secret:

```json
{
  "Version": "2012-10-17",
  "Statement": [{
    "Effect": "Allow",
    "Action": "secretsmanager:GetSecretValue",
    "Resource": "arn:aws:secretsmanager:eu-west-1:<account-id>:secret:booking/jwt-signing-key-*"
  }]
}
```

Add `kms:Decrypt` on the KMS key if the secret uses a customer-managed one.

### Rotation

The secret is read once at startup. Set `JWT_KEY_REFRESH_MINUTES` to a non-zero
value to have it re-read periodically: a new `kid` is then picked up without a
restart, and **public keys seen earlier are kept**, so access tokens issued just
before the rotation keep verifying until they expire. If a refresh fails, the
cached key stays in use and the failure is logged rather than breaking every
request.

With refresh disabled (`0`, the default), rotate with a rolling restart.

### Local development

Local runs read the secret the same way production does, so point the AWS SDK at
a local Secrets Manager (LocalStack, for instance) with the SDK's own endpoint
variable — no application setting is involved:

```bash
export AWS_ENDPOINT_URL_SECRETS_MANAGER=http://localhost:4566
export AWS_REGION=eu-west-1
export AWS_ACCESS_KEY_ID=test
export AWS_SECRET_ACCESS_KEY=test
export JWT_SECRET_ID=booking/jwt-signing-key

aws --endpoint-url http://localhost:4566 secretsmanager create-secret \
  --name booking/jwt-signing-key --secret-string file:///tmp/jwt-secret.json
```

Otherwise point `JWT_SECRET_ID` at a real development secret in AWS. Either way
the key never lives on disk in the project, and `*.pem` is git-ignored.

---

## Database setup

Full step-by-step instructions, including verification commands, live in
[`db/README.md`](db/README.md). The short version:

### PostgreSQL (default)

```bash
# 1. role + database (as superuser)
psql -U postgres -h localhost -f db/postgresql/01_create_database.sql
# 2. schema
psql -U booking_app -h localhost -d booking_db -f db/postgresql/02_schema.sql
# 3. verify
psql -U booking_app -h localhost -d booking_db -c '\dt'
```

```bash
export DB_URL=jdbc:postgresql://localhost:5432/booking_db
export DB_USERNAME=booking_app
export DB_PASSWORD=<your password>
```

### MySQL

Switching engines is **configuration only** — no code change, no rebuild.

```bash
# 1. user + database (as root)
mysql -u root -p < db/mysql/01_create_database.sql
# 2. schema
mysql -u booking_app -p booking_db < db/mysql/02_schema.sql
# 3. verify
mysql -u booking_app -p -e 'SHOW TABLES;' booking_db
```

```bash
export SPRING_PROFILES_ACTIVE=mysql
export DB_URL='jdbc:mysql://localhost:3306/booking_db?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true'
export DB_USERNAME=booking_app
export DB_PASSWORD=<your password>
```

Both drivers ship with the application; the JDBC URL selects the dialect.

### Schema

| Table | Purpose |
|---|---|
| `users` | Accounts: `username` (unique), BCrypt `password`, `role` |
| `resources` | Bookable resources with `type`, `active` and audit columns |
| `reservations` | `resource_id`, `user_id`, period, `status`, `price DECIMAL(10,2)` |
| `refresh_tokens` | SHA-256 **hash** of the opaque token, expiry, revoked flag |
| `audit_logs` | actor, action, entity, timestamp, correlation id |

---

## Build and run

```bash
# build and run the full test suite
mvn clean verify

# run against the configured database
mvn spring-boot:run

# or run the packaged jar
java -jar target/resource-booking-system-1.0.0.jar
```

With a `.env` file:

```bash
set -a && source .env && set +a && mvn spring-boot:run
```

The application starts on <http://localhost:8080>.

---

## Seed users (test only)

On first start the seeder creates two accounts and three sample resources if they
are absent. **These are development credentials, documented for testing only —
change or remove them before any real deployment.**

| Username | Password | Role |
|---|---|---|
| `admin` | `Admin@123` | `ADMIN` |
| `user` | `User@123` | `USER` |

Sample resources: *Meeting Room Alpha* (`ROOM`), *Company Van* (`VEHICLE`),
*4K Projector* (`EQUIPMENT`).

---

## Swagger / OpenAPI

| What | URL |
|---|---|
| Swagger UI | <http://localhost:8080/swagger-ui.html> |
| OpenAPI JSON | <http://localhost:8080/v3/api-docs> |

Click **Authorize** and paste an access token; Swagger sends it as
`Authorization: Bearer <token>`.

---

## Token flow with curl

### 1. Login

```bash
curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"User@123"}'
```

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "n0Yy3...opaque...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

The access token is a 15-minute RS256 JWT carrying `sub`, `userId`, `role` and
`jti`, with the signing key's `kid` in its header:

```json
{"kid":"booking-2026-09","alg":"RS256"}
```

The refresh token is an opaque random value; only its SHA-256 hash is stored
server-side. Any service holding just the public key can verify an access token
on its own:

```bash
openssl dgst -sha256 -verify jwt-public.pem -signature sig.bin signing-input.bin
# Verified OK
```

```bash
ACCESS=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"User@123"}' | jq -r .accessToken)
REFRESH=$(curl -s -X POST http://localhost:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"user","password":"User@123"}' | jq -r .refreshToken)
```

### 2. Call an endpoint

```bash
curl -s http://localhost:8080/api/resources -H "Authorization: Bearer $ACCESS"

curl -s -X POST http://localhost:8080/api/reservations \
  -H "Authorization: Bearer $ACCESS" \
  -H 'Content-Type: application/json' \
  -d '{"resourceId":1,"startTime":"2030-01-01T09:00:00","endTime":"2030-01-01T10:00:00","price":100.50}'
```

The reservation owner always comes from the token. A `userId` in the body is
ignored, so a `USER` cannot create a booking on somebody else's behalf.

### 3. Refresh (rotating)

```bash
curl -s -X POST http://localhost:8080/auth/refresh \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
```

Every refresh returns a **new pair** and revokes the token you presented. Replaying
an old, expired or unknown refresh token returns `401`.

### 4. Logout

```bash
curl -s -X POST http://localhost:8080/auth/logout -H "Authorization: Bearer $ACCESS" -i
```

Returns `204`. All refresh tokens of the caller are revoked and the current
access token's `jti` is blacklisted until it would have expired, so both tokens
return `401` afterwards.

---

## API reference

### Authentication

| Method | Path | Access | Description |
|---|---|---|---|
| `POST` | `/auth/login` | public | Username + password → token pair |
| `POST` | `/auth/refresh` | public | Rotate a refresh token |
| `POST` | `/auth/logout` | authenticated | Revoke refresh tokens, blacklist the access token |

### Resources

| Method | Path | Access |
|---|---|---|
| `GET` | `/api/resources` | `ADMIN`, `USER` |
| `GET` | `/api/resources/{id}` | `ADMIN`, `USER` |
| `POST` | `/api/resources` | `ADMIN` |
| `PUT` | `/api/resources/{id}` | `ADMIN` |
| `DELETE` | `/api/resources/{id}` | `ADMIN` |

### Reservations

| Method | Path | Access |
|---|---|---|
| `POST` | `/api/reservations` | `ADMIN`, `USER` (owner taken from the token) |
| `GET` | `/api/reservations` | `ADMIN` sees all, `USER` sees only their own |
| `GET` | `/api/reservations/{id}` | `ADMIN` any, `USER` only their own |
| `PUT` | `/api/reservations/{id}` | `ADMIN` |
| `DELETE` | `/api/reservations/{id}` | `ADMIN` |

A `USER` requesting another user's reservation gets **404**, never 403 — the API
does not confirm that the row exists.

#### Query parameters of `GET /api/reservations`

| Parameter | Rules |
|---|---|
| `status` | `PENDING`, `CONFIRMED` or `CANCELLED` |
| `minPrice`, `maxPrice` | ≥ 0, at most 2 decimals, `minPrice <= maxPrice` |
| `page` | ≥ 0 (default `0`) |
| `size` | 1–100 (default `20`) |
| `sort` | `property,direction`; default `id,desc` |

Sorting is whitelisted to `id`, `startTime`, `endTime`, `price`, `status` and
`createdAt`; anything else is a `400`. Filtering is implemented with JPA
Specifications.

```bash
curl -s "http://localhost:8080/api/reservations?status=CONFIRMED&minPrice=50&maxPrice=300&page=0&size=20&sort=price,asc" \
  -H "Authorization: Bearer $ACCESS"
```

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 3,
  "totalPages": 1,
  "first": true,
  "last": true,
  "sort": "price: ASC"
}
```

---

## Refresh tokens and session revocation

### Rotation, and what happens when a token is replayed

Every refresh returns a new pair and revokes the one presented. A legitimate
client therefore always holds the newest token — so a **second** use of a token
that rotation already consumed means two parties hold the same credential, which
is what a stolen token looks like.

On detecting that, every refresh token of that user is revoked. The thief's
session ends, and so does the victim's: they log in again, the thief cannot.
This is the behaviour the OAuth 2.0 security BCP asks for, and it is why revoked
tokens are **kept** until they expire rather than deleted on rotation — deleting
them would destroy the evidence that a replay is happening.

The 401 body is identical for unknown, expired, revoked and replayed tokens, so
probing cannot tell them apart. The distinction is recorded server-side:
`REFRESH_TOKEN_REUSE_DETECTED` in `audit_logs`, and a `WARN` log line.

One limit worth knowing: revocation covers refresh tokens. An access token
already minted stays valid until it expires (at most 15 minutes) unless it is
also blacklisted, which happens on logout but not on reuse detection — there is
no way to enumerate a user's outstanding `jti`s.

### Where refresh tokens live

Behind a `RefreshTokenStore` interface, implemented by `JpaRefreshTokenStore`
against the `refresh_tokens` table. Only a SHA-256 hash of each token is stored.
The interface exists so the storage can be swapped — for Redis, for instance —
without touching login, refresh or logout; the same arrangement as
`TokenBlacklist` and `JwtKeyProvider`.

Relational storage is the default on purpose: these are seven-day credentials,
the write rate is one row per login or refresh (not per request), and the table
gives session listing, an audit trail and a foreign key to `users` for free. A
Redis-backed store becomes worthwhile at a login rate where writes to the
primary database hurt; if you go there, keep revoked entries until their natural
expiry, or replay detection stops working.

### Expired tokens are swept

`ExpiredRefreshTokenPurger` runs on `app.auth.refresh-token-purge-cron` (hourly
at :15 by default) and deletes tokens past their expiry — and only those.
Revoked-but-unexpired tokens are left alone for the reason above. Several
instances running the sweep at once is harmless.

### The access-token blacklist

Logout blacklists the access token's `jti` until it would have expired. Two
backends sit behind `TokenBlacklist`:

| `TOKEN_BLACKLIST` | Backend | Use |
|---|---|---|
| `in-memory` (default) | `ConcurrentHashMap` in the process | One instance, and tests |
| `redis` | Redis key per `jti`, TTL = remaining token life | **Required for more than one replica** |

With more than one replica the in-memory backend is not merely inefficient, it
is wrong: a logout served by one instance leaves the token working on the
others until it expires. Verified on two instances — with `in-memory`, the
token stayed valid on the second instance after logout; with `redis`, both
rejected it.

The `redis-blacklist` profile sets the backend and adds Redis to the readiness
probe, since Redis then gates authentication:

```bash
export SPRING_PROFILES_ACTIVE=redis-blacklist
export REDIS_HOST=... REDIS_PORT=6379 REDIS_PASSWORD=...
```

Redis failures are not swallowed. A logout that reported success without
revoking anything, or a request admitted because the revocation list could not
be read, are both worse than a visible error.

---

## Error shape

Every failure — from a servlet filter, from Spring Security or from a controller —
uses the same body:

```json
{
  "timestamp": "2026-09-18T07:41:42.136142271Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "path": "/api/reservations",
  "correlationId": "7b0333b0-9f80-4fb3-8832-604001940700",
  "fieldErrors": [
    { "field": "endTime", "message": "endTime must be after startTime" },
    { "field": "price", "message": "price must be zero or greater" }
  ]
}
```

| Code | When |
|---|---|
| `400` | Validation failure, bad enum, bad paging or sorting |
| `401` | Missing, expired, malformed, wrongly signed or blacklisted token; bad credentials; dead refresh token |
| `403` | Authenticated but the role is insufficient |
| `404` | Unknown entity, or a reservation the caller may not see |
| `409` | Duplicate resource name, inactive resource, overlapping booking |

`401` and `403` are JSON too, produced by a custom `AuthenticationEntryPoint` and
`AccessDeniedHandler`.

---

## Health endpoints

Only `health` and `info` are exposed; everything else returns `404`.

| Endpoint | Purpose |
|---|---|
| `/actuator/health` | Overall status |
| `/actuator/health/liveness` | Liveness probe |
| `/actuator/health/readiness` | Readiness probe — **includes the database check** |
| `/actuator/info` | Build/application info |

All four are public, so orchestrators can probe them without a token. Details are
`when-authorized` and limited to `ADMIN`:

```bash
curl -s http://localhost:8080/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

curl -s http://localhost:8080/actuator/health -H "Authorization: Bearer $ADMIN_TOKEN"
# {"components":{"db":{"details":{"database":"PostgreSQL",...},"status":"UP"}, ...}
```

---

## Logging and the correlation ID

Logs are structured JSON on stdout in **Elastic Common Schema**, via Spring Boot's
built-in structured logging (`logging.structured.format.console=ecs`):

```json
{"@timestamp":"2026-09-18T07:41:59.455Z","log":{"level":"INFO","logger":"com.exelynt.booking.common.logging.RequestLoggingFilter"},
 "message":"http_request method=GET path=/api/reservations status=200 durationMs=12",
 "correlationId":"a77bed76-...","username":"user","ecs":{"version":"8.11"}}
```

* `CorrelationIdFilter` runs **first**: it reads `X-Correlation-Id` or generates a
  UUID, puts it in the MDC, echoes it in the response header and clears the MDC
  afterwards. A client-supplied value is accepted only if it is short and
  alphanumeric (`-`, `_`, `.`), otherwise a fresh UUID is used — a header from the
  outside never lands verbatim in a log line.
* The authenticated `username` is added to the MDC once the JWT filter has
  established the identity.
* Each request is logged exactly once with method, path, status and duration.
* Passwords, tokens, the `Authorization` header and secrets are **never** logged.
  `LoginRequest`, `RefreshRequest`, `TokenResponse` and `User` override
  `toString()` so they cannot leak by accident.

```bash
curl -s -D - -o /dev/null http://localhost:8080/actuator/health -H 'X-Correlation-Id: my-trace-123'
# X-Correlation-Id: my-trace-123
```

The same id appears in the error body of a failed request, which is what ties a
user-visible failure to its log lines.

---

## Audit trail

Two complementary mechanisms:

* **JPA auditing** on `Resource` and `Reservation` fills `createdAt`,
  `updatedAt`, `createdBy` and `updatedBy`; the `AuditorAware` reads the username
  from the security context (`system` during startup seeding).
* **`audit_logs` rows** are written for login success, login failure, logout,
  token refresh (and refresh failure), refresh-token replay detection
  (`REFRESH_TOKEN_REUSE_DETECTED`), and every create / update / delete / status
  change on resources and reservations. Each row records the actor, the
  action, the entity, the timestamp and the correlation id of the request.

---

## Tests

```bash
mvn test
```

118 tests, all green:

* **Unit** — refresh-token replay detection and the cases it must *not* fire on
  (expired, expired-and-revoked, unknown), `JpaRefreshTokenStore` (only a hash
  is stored, revoke keeps the row, the sweep drops expired rows only), the
  expiry sweep, `RedisTokenBlacklist` (TTL matches the token's remaining life,
  outages surface), `JwtService` (claims, expiry, wrong signature, unknown `kid`, wrong
  issuer, malformed, `alg: none`, HS256 algorithm confusion, and verification
  across a key rotation), `RsaKeys` PEM parsing and public-key derivation, the
  AWS Secrets Manager provider (JSON and bare-PEM secrets, caching, rotation,
  failed refresh, missing secret), `JwtProperties` fail-fast rules, the token
  blacklist, the sort whitelist, and the auth / resource / reservation services
  with Mockito.
* **Integration (MockMvc)** — login success and failure; expired, malformed and
  wrongly signed tokens return 401; `USER` gets 403 on admin endpoints; `USER`
  cannot read another user's reservation (404); a `userId` in the request body is
  ignored; filtering, paging and sorting; refresh rotation; access and refresh
  after logout return 401; replaying a rotated refresh token ends every session
  of that user and is indistinguishable from an unknown token; the correlation
  ID header is echoed back; health
  endpoint exposure and detail visibility.

Tests run under the `test` profile against in-memory H2. They exercise the real
`AwsSecretsManagerJwtKeyProvider`: only the SDK client is stubbed, returning a
secret in the production JSON shape whose key pair is generated per JVM run. So
the production key path — fetch, parse, derive, fingerprint — is the one under
test, no AWS call leaves the JVM, no environment variables are needed, and no
key material is committed. `SecretsManagerKeySourceTest` additionally asserts
that Secrets Manager is the *only* key provider in the context and that no
property can carry a key.

---

## Project layout

```
src/main/java/com/exelynt/booking
├── BookingApplication.java
├── auth/          login, refresh rotation with replay detection, logout,
│                  RefreshTokenStore (+ JPA implementation), expiry sweep
├── audit/         AuditLog entity, repository, AuditService
├── common/
│   ├── exception/ error shape + @RestControllerAdvice
│   ├── logging/   correlation-id filter, request log filter, MDC helpers
│   ├── model/     Auditable mapped superclass, PageResponse
│   ├── validation/ @ValidPeriod, @ValidPriceRange
│   └── web/       JSON error writer, sort whitelist
├── config/        JPA auditing, OpenAPI, data seeder
├── reservation/   entity, repository, specifications, service, controller, DTOs
├── resource/      entity, repository, service, controller, DTOs
├── security/      RS256 JWT service, AWS Secrets Manager key provider, filter,
│                  blacklist, principal, security config
└── user/          User entity, repository, service
```

Layering is strict: **controller → service → repository**. DTOs are the only
types crossing the API boundary; entities never are. All injection is
constructor-based.

---

## Security notes

* Access tokens are signed with RS256. The private key never leaves the
  application, and verifiers need only the public key, so no downstream service
  has to hold a signing secret.
* The parser pins the algorithm to RS256 and resolves the key by `kid`, which
  closes the two classic asymmetric-JWT holes: `alg: none`, and an HMAC token
  forged with the (freely available) public key as the secret. Both are covered
  by tests.
* The signing key lives in AWS Secrets Manager and nowhere else. There is no
  configuration property, environment variable or file from which the
  application will accept a key, and it cannot generate one, so no key material
  and no AWS credentials appear in configuration, in the repository, or in any
  log line.
* Passwords are hashed with BCrypt; the raw value is never stored, logged or returned.
* Refresh tokens are 64 bytes of `SecureRandom` output; only the SHA-256 hash is
  persisted, so a database dump cannot be replayed against the API.
* Refresh tokens rotate on every use. Replaying a consumed one is treated as
  theft: every session of that user is revoked, and the 401 is worded
  identically to the one for an unknown token.
* Logout revokes refresh tokens and blacklists the access token's `jti` until its
  natural expiry. Use the Redis backend whenever more than one replica runs: the
  in-memory one only revokes on the instance that served the logout.
* Login failures return the same message for an unknown user and a wrong
  password, so accounts cannot be enumerated.
* Sessions are disabled entirely (`SessionCreationPolicy.STATELESS`); CSRF is off
  because there is no cookie-based authentication to protect.
* `sort` is whitelisted, so the parameter cannot be used to probe arbitrary columns.
* The seeded credentials above exist for local testing only.
