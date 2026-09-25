# Operations: Docker, Kubernetes and running in production

## Docker image

```bash
docker build -t stubu:latest .
```

The `Dockerfile` builds the application in a JDK image (including the front end) and runs it in a small JRE image as a
normal user (not root). The image

* starts without manual steps (`SPRING_PROFILES_ACTIVE=prod` is set; you may override it),
* is configured **only through environment variables** (see the reference in [DEVELOPMENT.md](../DEVELOPMENT.md)),
* keeps no state on disk (all state is in PostgreSQL and in the user's session in memory),
* sizes its heap from the memory limit of the container (`-XX:MaxRAMPercentage=75`),
* has a `HEALTHCHECK` for plain Docker, and stops gracefully on `SIGTERM`.

A minimal run:

```bash
docker run --rm -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://db:5432/stubu \
  -e SPRING_DATASOURCE_USERNAME=stubu -e SPRING_DATASOURCE_PASSWORD=... \
  -e STUBU_IAM_PROVIDERS_MICROSOFT_TENANTID=... -e STUBU_IAM_PROVIDERS_MICROSOFT_CLIENTID=... \
  -e STUBU_IAM_PROVIDERS_MICROSOFT_CLIENTSECRET=... \
  stubu:latest
```

Without HTTPS (local trial only) also pass `-e SESSION_COOKIE_SECURE=false`; the session cookie is otherwise only sent
over HTTPS.

Flyway migrates the database when the application starts. With several pods starting at once Flyway takes a database
lock, so one pod migrates and the others wait.

> Verified (Docker 29, PostgreSQL 18): the image builds, runs as a non-root user, migrates an empty database, reports
> healthy, stops gracefully on `SIGTERM`, and runs with `--read-only --tmpfs /tmp --cap-drop ALL` and a 1 GiB memory limit
> (about 350 MiB used). Two instances started at the same moment against an empty database migrated it once and both
> came up. Sign-in with a real provider was not part of that check.

## Health checks

| Endpoint | Meaning | Used for |
|----------|---------|----------|
| `/actuator/health/liveness` | The process is running and not stuck | Kubernetes liveness and startup probe, Docker `HEALTHCHECK` |
| `/actuator/health/readiness` | The application can serve requests: it is not shutting down and the database answers | Kubernetes readiness probe |
| `/actuator/health` | Overall status | Manual checks |

They need no sign-in and show only `UP` or `DOWN`, no details. No other actuator endpoint is exposed.

## Kubernetes

The manifests are in [`deploy/kubernetes/`](../deploy/kubernetes/):

| File | Content |
|------|---------|
| `configmap.yaml` | Settings that are not secret (database URL, sign-in client ids, notification sender, retention) |
| `deployment.yaml` | 2 replicas, rolling update without downtime, probes, resource requests and limits, non-root, read-only file system, graceful termination |
| `service.yaml` | Cluster-internal service |
| `ingress.yaml` | TLS ingress with **sticky sessions** (NGINX annotations) and long timeouts for the push connection |
| `poddisruptionbudget.yaml` | At least one pod stays up while nodes are drained |
| `examples/secret.example.yaml` | Shape of the secret. **Placeholders only**; create the real secret in the cluster |

```bash
kubectl create secret generic stubu-secrets \
  --from-literal=SPRING_DATASOURCE_USERNAME=stubu \
  --from-literal=SPRING_DATASOURCE_PASSWORD='...' \
  --from-literal=STUBU_IAM_PROVIDERS_MICROSOFT_CLIENTSECRET='...'
kubectl apply -f deploy/kubernetes/
```

Adjust the image name, host name, database URL and provider ids first. The manifests were checked for syntax and for
agreement with the application (`KubernetesManifestsTest`) and **applied to a single-node Docker Desktop cluster** with a
PostgreSQL pod. There, both replicas became ready, a complete rolling restart under continuous requests (699 requests
through the Service) produced no failed request, and a deleted pod was replaced and ready within about 16 seconds.
Not tried: the ingress (no ingress controller was installed, so the sticky-session annotations are untested), node drains
with the pod disruption budget, and sign-in through a real provider.

> The pod's user has a **number** (10001) in the Dockerfile and in `securityContext`: Kubernetes cannot verify that a
> user given by name is not root and refuses to start such a container when `runAsNonRoot` is set.

### A development namespace

`deploy/kubernetes/dev/postgres.yaml` is a small PostgreSQL (one instance, 1 GiB volume, no backups) for a development
or test namespace; never use it for production. Its user and password come from the same secret as the application's.
A local setup (Docker Desktop's Kubernetes) in the namespace `stubu-dev`:

```bash
TAG=dev-$(git rev-parse --short HEAD)-$(date +%H%M%S)   # a new tag for every build, see below
docker build -t stubu:$TAG .
kubectl create namespace stubu-dev
kubectl -n stubu-dev create secret generic stubu-secrets \n  --from-literal=SPRING_DATASOURCE_USERNAME=stubu --from-literal=SPRING_DATASOURCE_PASSWORD="$(openssl rand -hex 24)"
kubectl -n stubu-dev apply -f deploy/kubernetes/dev/postgres.yaml
# the manifests with the local database host, image and no HTTPS-only cookie (edit configmap.yaml instead of using sed if you prefer)
sed -e 's#postgres.example.svc#postgres#' -e 's#SESSION_COOKIE_SECURE: "true"#SESSION_COOKIE_SECURE: "false"#' \n    -e '/STUBU_IAM_PROVIDERS_MICROSOFT/d' deploy/kubernetes/configmap.yaml | kubectl -n stubu-dev apply -f -
sed -e "s#registry.example.com/stubu:latest#stubu:$TAG#" -e 's#replicas: 2#replicas: 1#' deploy/kubernetes/deployment.yaml \n    | kubectl -n stubu-dev apply -f -
kubectl -n stubu-dev apply -f deploy/kubernetes/service.yaml
kubectl -n stubu-dev port-forward svc/stubu 8080:80    # then open http://localhost:8080
```

Signing in needs an identity provider (see DEVELOPMENT.md) and a first administrator in the database.

**Deploy a new version with a new image tag** (`kubectl -n stubu-dev set image deployment/stubu stubu=stubu:$TAG`), never by
rebuilding the same tag and restarting: the cluster node keeps the image it first resolved for a tag and starts the old
version again (found when a redeploy silently ran the previous build). Check what runs with
`kubectl -n stubu-dev get pod -o jsonpath='{.items[*].status.containerStatuses[0].imageID}'` and compare it with
`docker inspect stubu:$TAG --format '{{.Id}}'`.

### Several pods and sessions

The application is stateless apart from the user's **session**, which holds the state of the open pages, the message
for the next page, and the chosen manager scope. The consequences:

* Run several replicas behind an ingress with **cookie-based sticky sessions** (configured in `ingress.yaml`). Every
  request of a browser then reaches the pod that holds its session.
* If a pod is replaced (a rollout, a node drain, a crash), the users on it are shown the login page and sign in again;
  the identity provider makes that a single click. No data is lost: every save is already in the database.
* Correctness does not depend on stickiness: the rules that must hold across pods (one open work period, one timesheet
  per month, no double decision, no lost update) are enforced by database constraints and optimistic locking.
* Replicating sessions between pods (Spring Session with JDBC or Redis) is possible but not needed and not enabled; it
  would require the views' state to be serializable.

### Graceful shutdown

On `SIGTERM` the application stops accepting new requests and finishes running ones for up to 30 seconds
(`spring.lifecycle.timeout-per-shutdown-phase`). The pod's `terminationGracePeriodSeconds` (60) covers that plus the
10 seconds `preStop` wait that lets the ingress remove the pod first.

## Logging and observability

* The `prod` profile writes **one JSON document per line** (Elastic Common Schema) to the console, ready for Loki,
  Elastic, CloudWatch and similar collectors. Locally the readable format is used.
* Errors are logged with their stack trace and enough context (employee id, entity id) to find the case; **no tokens,
  passwords or client secrets are logged**. Email addresses appear in the audit log (which needs them) and in the
  "no mail server configured" log line of development setups.
* The functional history (who changed what) is the audit log in the database, visible to administrators under
  "Audit log".
* For metrics, add `management.endpoints.web.exposure.include=health,prometheus` with the Micrometer Prometheus
  registry and scrape it on a **separate management port** that is not exposed by the ingress. It is not enabled by
  default because it is not required.

## Data retention

`stubu.retention.time-records` (default `P1Y`, one year) states how long time records are kept. **Nothing is deleted
automatically**: deleting employee time records needs explicit business requirements, a look at the audit
implications and a legal review first (spec.md section 32). `RetentionPolicy` reports which records are beyond the
period, and the application logs the configured period at startup. Backups of the database are part of the retention
concept as well and are the operator's responsibility.

## Audit log protection

The audit log is append-only. The application has no operation that changes or deletes an entry, and on PostgreSQL
triggers (migration `V7`) refuse `UPDATE`, `DELETE` and `TRUNCATE`. Also withhold those rights from the application's database
user. If a retention job ever has to remove old entries, it needs its own migration that deliberately replaces the
triggers (and a legal and audit review first).

## Backup and restore

All data is in PostgreSQL. Back it up with the tools of your platform (managed-database snapshots, `pg_dump`,
point-in-time recovery). The application can be redeployed from the image at any time. Restore the database first,
then start the pods; Flyway brings an older backup up to the current schema.

## Scaling

The application supports about 10,000 employees. Lists that can get long are paged in the database (audit log,
administrator's employee list). The manager's approval list is limited by the number of people a manager covers and is
not paged. Add replicas for more concurrent users; the database is the shared resource, so size its connection pool
(`spring.datasource.hikari.maximum-pool-size`, default 10 per pod) with the number of pods in mind.
