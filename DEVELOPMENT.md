# Development

Java 25 is required (`JAVA_HOME`). The Maven wrapper is included; the commands work unchanged in PowerShell, cmd and bash.

## Build and run

```bash
./mvnw clean package              # production build (JAR in target/)
./mvnw -Dvaadin.skip=true test    # fast tests, no browser
./mvnw test -Pe2e                 # browser tests (see below)
./mvnw test -Dtest=ClassName      # one test class
```

`-Dvaadin.skip=true` skips the front-end build, which the fast tests do not need.

### Local development with sign-in

```bash
./mvnw spring-boot:test-run
```

The application runs on <http://localhost:8080> with H2 in memory and the `dev` profile; the mock identity provider
runs on port 9000 (started by `src/test/java/com/stubu/specdriven/DevApplication.java`, which is test code, so the
mock never ships).
On its sign-in page enter one of the sample emails from `src/main/resources/db/dev/R__dev_seed_data.sql`:

| Email | Role |
|-------|------|
| `alice.employee@example.com` | employee |
| `bob.manager@example.com` | manager |
| `carol.admin@example.com` | administrator |
| `erik.employee@example.com` | employee |
| `dave.inactive@example.com` | deactivated (cannot sign in) |

The sample data includes a draft and a rejected timesheet of Alice, a submitted one of Erik, and the public holiday
"Company Day". To run without opening a browser window add
`"-Dspring-boot.run.jvmArguments=-Dvaadin.launch-browser=false"`.

## Profiles and databases

| Profile | Database | Used for |
|---------|----------|----------|
| none (default) | PostgreSQL | production; connection from the environment |
| `dev` | H2 in memory, PostgreSQL mode, sample data | local development |
| `test` | H2 in memory, PostgreSQL mode | automated tests (`@ActiveProfiles("test")`) |
| `prod` | as the default, plus structured JSON logs | the Docker image (`SPRING_PROFILES_ACTIVE=prod`) |

The schema is owned by Flyway (`src/main/resources/db/migration`, `V1` to `V6`); Hibernate only validates it
(`ddl-auto=validate`), so tests and development run the migrations that production runs. Migrations must be portable
SQL that runs unchanged on H2 and PostgreSQL. The one exception is `db/vendor/postgresql/V7__audit_log_append_only.sql`
(triggers that make the audit log append-only), which Flyway reads only on PostgreSQL. An applied migration is never
edited; add a new one.

### PostgreSQL

Create an empty database and a user that owns it, then set the connection in the environment:

```bash
createdb stubu
export SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/stubu
export SPRING_DATASOURCE_USERNAME=stubu
export SPRING_DATASOURCE_PASSWORD=...
./mvnw spring-boot:run
```

Flyway creates the tables at the first start. The first administrator has to be inserted by hand (an employee is never
created from a login), for example:

```sql
INSERT INTO department (name, created_at) VALUES ('Management', now());
INSERT INTO employee (email, first_name, last_name, role, department_id, is_active, created_at, updated_at)
VALUES ('first.admin@example.com', 'First', 'Admin', 'ADMIN',
        (SELECT id FROM department WHERE name = 'Management'), TRUE, now(), now());
```

The administrator then adds everybody else on the "Employees" page. Give the database user of the application only
the rights it needs (`SELECT`, `INSERT`, `UPDATE`, `DELETE` on the tables; the schema is changed by Flyway, so run
the migration with a more privileged user if you separate the two). Withhold `UPDATE` and `DELETE` on `audit_log`: on
PostgreSQL triggers already refuse them, and the missing rights are a second barrier.

### Sign-in providers (OpenID Connect)

Providers are configured with environment variables and appear on the login page only when their client id is set.
Register the application at the provider with the redirect URI `{base-url}/login/oauth2/code/{provider}`, for
example `https://stubu.example.com/login/oauth2/code/microsoft`.

| Provider | Register at | Settings |
|----------|-------------|----------|
| Microsoft Entra ID | App registrations, "Web" platform | `STUBU_IAM_PROVIDERS_MICROSOFT_TENANTID`, `..._CLIENTID`, `..._CLIENTSECRET` |
| Google | Google Cloud console, OAuth client "Web application" | `STUBU_IAM_PROVIDERS_GOOGLE_CLIENTID`, `..._CLIENTSECRET` |
| Any other OIDC provider | its admin console | `STUBU_IAM_PROVIDERS_<ID>_CLIENTID`, `..._CLIENTSECRET`, `..._AUTHORIZATIONURI`, `..._TOKENURI`, `..._JWKSETURI` (and `..._DISPLAYNAME`, `..._SCOPES`) |

The application only needs the email address from the ID token (`email`, or `preferred_username` / `upn` at Entra ID;
an address the provider flags as unverified is refused). The email must match the email of an active employee.

### Email notifications

Without a mail server nothing is sent and the notification is only logged. To send emails set `SPRING_MAIL_HOST`
(and `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD`, plus the usual `spring.mail.properties.*` for
STARTTLS). Managers are told about submitted timesheets, employees about decisions. A failed delivery is logged and
never undoes the action that caused it.

## Configuration reference

Every setting can be given as an environment variable (dots and dashes become underscores, upper case); nothing
requires a rebuild. Secrets belong in the environment or a secret store, never in the repository.

| Property | Environment variable | Default | Meaning |
|----------|----------------------|---------|---------|
| `server.port` | `PORT` | 8080 | HTTP port |
| `spring.datasource.url` | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/stubu` | Database |
| `spring.datasource.username` / `password` | `SPRING_DATASOURCE_USERNAME` / `_PASSWORD` | – | Database credentials |
| `server.servlet.session.cookie.secure` | `SESSION_COOKIE_SECURE` | `true` | Session cookie only over HTTPS; `false` only for plain-http local use |
| `stubu.iam.providers.<id>.*` | `STUBU_IAM_PROVIDERS_<ID>_*` | none | Sign-in providers, see above |
| `stubu.security.recheck-interval` | `STUBU_SECURITY_RECHECKINTERVAL` | `PT5S` | How often a signed-in employee is checked against the employee record; a deactivation or role change takes effect after at most this long |
| `stubu.time-tracking.refresh-interval` | `STUBU_TIMETRACKING_REFRESHINTERVAL` | `PT30S` | How often an open time tracking page refreshes itself |
| `stubu.retention.time-records` | `STUBU_RETENTION_TIME_RECORDS` | `P1Y` | How long time records are kept (reported only, nothing is deleted) |
| `stubu.notifications.from` | `STUBU_NOTIFICATIONS_FROM` | `noreply@stubu.local` | Sender of the emails |
| `stubu.notifications.locale` | `STUBU_NOTIFICATIONS_LOCALE` | `en` | Language of the emails (`en`, `de`) |
| `stubu.notifications.zone` | `STUBU_NOTIFICATIONS_ZONE` | `UTC` | Time zone of the times in the emails |
| `spring.mail.*` | `SPRING_MAIL_*` | unset | Mail server; without a host emails are only logged |
| `server.shutdown` | – | `graceful` | Finish running requests on shutdown |
| `spring.lifecycle.timeout-per-shutdown-phase` | – | `30s` | Longest wait for them |
| `management.endpoints.web.exposure.include` | – | `health` | Only the health probes are exposed |

## Tests

* **Fast tests** (`./mvnw -Dvaadin.skip=true test`) need neither a browser nor Docker. They are organised per use
  case (`usecases/ucNNN_…/UC00NName.java`, Vaadin browserless tests plus `@SpringBootTest` service tests), plus
  cross-cutting tests (`security`, `operations`, `retention`). `SpringBrowserlessTest` drives the views without a
  browser; `@WithEmployee` signs a test employee in; `MutableClock` controls the server time.
* **Browser tests** (`./mvnw test -Pe2e`, tag `e2e`, classes `…E2E`) start the whole application on a random port with
  a test identity provider and drive the Chrome installed on the machine (Playwright for Java, headless). Nothing is
  downloaded at run time. Each screen is checked at 1920x1080, 768x1024 and 375x812: no horizontal scrolling, large
  touch targets, aligned layout and WCAG AA text contrast. Screenshots go to `target/e2e-screenshots/`.

  | Option | Effect |
  |--------|--------|
  | `-De2e.headed=true` | Show the browser window |
  | `-De2e.browserChannel=msedge` | Use Edge instead of Chrome (empty: Playwright's own Chromium, needs `playwright install`) |
  | `-Dtest=UC002*E2E` | Run one class |

* **PostgreSQL tests** (`…PostgresTest`, Testcontainers) run the migrations and the database-specific queries on a real
  PostgreSQL. They are skipped automatically when Docker is not available. `./mvnw -Dvaadin.skip=true test
  -Dtest='*PostgresTest'` runs only them.

## Docker and Kubernetes

See [docs/operations.md](docs/operations.md). In short:

```bash
docker build -t stubu:latest .
docker run -p 8080:8080 -e SPRING_DATASOURCE_URL=... -e SPRING_DATASOURCE_USERNAME=... \
  -e SPRING_DATASOURCE_PASSWORD=... -e STUBU_IAM_PROVIDERS_MICROSOFT_TENANTID=... stubu:latest
```

If you use commercial Vaadin components, pass the license key as a build secret:
`docker build --secret id=proKey,src=$HOME/.vaadin/proKey .`
