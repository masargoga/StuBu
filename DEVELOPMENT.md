# Development

## Build & Run Commands

```bash
./mvnw                            # Run in dev mode (default goal: spring-boot:run)
./mvnw clean package              # Production build (JAR in target/)
./mvnw test                       # Run all tests
./mvnw test -Dtest=ClassName      # Run a single test class
```

The app runs on port 8080 (configurable via `PORT` env var).

### Local development with sign-in

Java 25 is required (`JAVA_HOME`). The command works unchanged in PowerShell, cmd and bash. To run the application with sample employees and a mock identity provider (no real Microsoft/Google account needed):

```bash
./mvnw spring-boot:test-run
```

The app runs on http://localhost:8080 and the mock identity provider on port 9000. On its sign-in page enter one of the sample emails from `src/main/resources/db/dev/R__dev_seed_data.sql`, e.g. `alice.employee@example.com` (employee), `bob.manager@example.com` (manager), `carol.admin@example.com` (admin) or `dave.inactive@example.com` (deactivated). Data lives in H2 in memory and is gone on restart.

### Browser tests (Playwright)

The regular `./mvnw test` runs fast tests without a browser. The tests that drive a real browser (Playwright for Java, tag `e2e`) run separately:

```powershell
.\mvnw test -Pe2e
```

They start the whole application on a random port with the test identity provider and a controllable server clock, and drive the Chrome installed on the machine (headless). Nothing is downloaded at run time. Options:

| Option | Effect |
|--------|--------|
| `-De2e.headed=true` | Show the browser window |
| `-De2e.browserChannel=msedge` | Use Edge instead of Chrome (empty value: Playwright's own Chromium, which needs `playwright install`) |
| `-Dtest=UC002*E2E` | Run one class |

Screenshots of every checked screen (desktop, tablet, mobile) are written to `target/e2e-screenshots/`. The tests also assert what a screenshot cannot: no horizontal scrolling, large buttons, stacked or side-by-side layout, aligned header and content, and WCAG AA text contrast.

### Production configuration

| Setting | Environment variable |
|---------|----------------------|
| Database | `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD` |
| Google login | `STUBU_IAM_PROVIDERS_GOOGLE_CLIENTID`, `STUBU_IAM_PROVIDERS_GOOGLE_CLIENTSECRET` |
| Microsoft login | `STUBU_IAM_PROVIDERS_MICROSOFT_TENANTID`, `..._CLIENTID`, `..._CLIENTSECRET` |
| Session cookie over plain HTTP (local only) | `SESSION_COOKIE_SECURE=false` |

## Docker

To build a Docker image, run:

```bash
docker build -t my-application:latest .
```

If you use commercial components, pass the license key as a build secret:

```bash
docker build --secret id=proKey,src=$HOME/.vaadin/proKey .
```
