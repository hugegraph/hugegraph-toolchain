# Server and Hubble Smoke Evidence

Date: 2026-06-21

## Server

Command:

```bash
docker ps --format 'table {{.Names}}\t{{.Image}}\t{{.Ports}}\t{{.Status}}'
```

Observed running container:

```text
observability-hugegraph-alert-1   hugegraph/hugegraph:1.5.0   0.0.0.0:18082->8080/tcp, [::]:18082->8080/tcp   Up
```

Command:

```bash
curl -fsS http://127.0.0.1:18082/versions
```

Result:

```json
{"versions":{"version":"v1","core":"1.5.0","gremlin":"3.5.1","api":"0.71.0.0"}}
```

## Hubble + Server Smoke

Command:

```bash
hugegraph-hubble/hubble-dist/assembly/travis/verify-hubble-issue-694.sh \
  hugegraph-hubble/target/apache-hugegraph-hubble-1.7.0.tar.gz \
  http://127.0.0.1:18082
```

Result:

```text
Hubble issue #694 smoke passed with connection id 1
```

This covers packaged Hubble startup, health, UI route fallback, Server
`/versions`, Hubble graph connection creation, and Hubble schema/job-manager/
async-task API availability. It does not cover Loader-backed import, direct
Gremlin count comparison, or direct shortestPath comparison.
