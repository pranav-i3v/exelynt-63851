# Database setup — step by step

The application never creates the schema for you (`spring.jpa.hibernate.ddl-auto`
defaults to `validate`). Run these scripts first, then start the app; Hibernate
validates the mapping against the schema at boot, and the seed loader inserts
the test users and sample resources.

Scripts are idempotent — re-running them is safe.

| Script | Purpose | Runs as |
|---|---|---|
| `01_create_database.sql` | Creates the `booking_app` login role and the `booking_db` database | DB superuser (`postgres`) |
| `02_schema.sql` | Creates the 5 tables, constraints and indexes | `booking_app` |
| `99_drop.sql` | Drops all tables (destructive teardown) | `booking_app` |

Change the sample password (`change_me_local_only`) in `01_create_database.sql`
before running it, and use the same value for the `DB_PASSWORD` environment
variable. No password is ever read from these files by the application.

---

## PostgreSQL

**Step 1 — create the role and the database** (as superuser):

```bash
psql -U postgres -h localhost -f db/postgresql/01_create_database.sql
```

**Step 2 — create the schema** (as the application role):

```bash
psql -U booking_app -h localhost -d booking_db -f db/postgresql/02_schema.sql
```

**Step 3 — verify the 5 tables exist:**

```bash
psql -U booking_app -h localhost -d booking_db -c '\dt'
```

Expected: `audit_logs`, `refresh_tokens`, `reservations`, `resources`, `users`.

**Step 4 — point the app at it** (in your `.env` / shell):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/booking_db
export DB_USERNAME=booking_app
export DB_PASSWORD=<the password from step 1>
```

**Step 5 — start the app.** On first start the seeder inserts `admin` / `user`
and 3 sample resources (see the root `README.md`).

**Step 6 (optional) — tear down:**

```bash
psql -U booking_app -h localhost -d booking_db -f db/postgresql/99_drop.sql
```

---

## Table overview

| Table | Columns |
|---|---|
| `users` | `id`, `username` (unique), `password` (BCrypt), `role` (`ADMIN`/`USER`), `enabled`, `created_at` |
| `resources` | `id`, `name` (unique), `type` (`ROOM`/`VEHICLE`/`EQUIPMENT`), `description`, `active`, audit columns |
| `reservations` | `id`, `resource_id` → `resources`, `user_id` → `users`, `start_time`, `end_time`, `status`, `price DECIMAL(10,2)`, audit columns |
| `refresh_tokens` | `id`, `user_id` → `users`, `token_hash` (SHA-256, unique), `expires_at`, `revoked`, `created_at` |
| `audit_logs` | `id`, `actor`, `action`, `entity_type`, `entity_id`, `event_time`, `correlation_id` |

Audit columns are `created_at`, `updated_at`, `created_by`, `updated_by`, filled
by JPA auditing (`AuditorAware` reads the username from the security context).

PostgreSQL is the only supported engine: the scripts use PostgreSQL types and
`CHECK` constraints, and only the PostgreSQL driver ships with the application.

Database-level guards mirror the Bean Validation rules: `end_time > start_time`,
`price >= 0`, and `CHECK` constraints on every enum column.
