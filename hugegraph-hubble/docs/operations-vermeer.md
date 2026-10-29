# Vermeer in Operations

The cluster overview keeps HugeGraph Server, PD and Store as the main topology.
Vermeer appears as a separate compute-service summary. Select it to enter the
compute view, and use **Return to main cluster** to restore the main view.
The compute topology places Master above Worker; Worker peers wrap within the
same layer as the deployment grows. Node details expose readiness checks and
process resource observations.

Operations describes the current deployment. Its top bar does not filter nodes
by the graph or GraphSpace selected elsewhere in Hubble. Existing Operations
health, topology and metrics permissions also apply to Vermeer.

## Configure the backend

Configure these properties in `conf/hugegraph-hubble.properties`:

```properties
operations.vermeer.enabled=true
operations.vermeer.address=http://vermeer-master:6688
operations.vermeer.token_file=/var/run/hugegraph/vermeer/token
```

`address` is the operator-configured Master HTTP origin, including its actual
port. It must not contain credentials, a path or a query. Hubble contacts only
this Master; addresses returned for Workers are displayed as metadata and do
not become browser-side request targets.

The token file contains a Vermeer administrator token. The backend reads it
without exposing it in API responses. A deployment using anonymous Vermeer
authentication may leave `token_file` empty. Do not use a signing factor as a
Bearer token. The HugeGraph Helm chart configures the service origin and mounts
its existing init-container-generated token read-only into Hubble; signing
material remains outside the running Hubble container.

Vermeer is disabled by default. Disabled integration is a neutral state and
does not lower the main cluster's health. The Dashboard enable flag is not used
as a live health check.

## Backend contract

Hubble reads `GET /api/v1/admin/cluster` from the configured Vermeer Master.
This requires a paired Vermeer build providing schema version 1, including:

- Master and Worker identities, roles, versions and registration metadata;
- per-Worker reverse-connection and load, compute and superstep stream checks;
- registered and ready Worker counts;
- independently timestamped process CPU/RSS observations and metric availability.

`GET /api/v1.3/operations/overview` retains its existing `nodes`, `facts` and
`sources` for the main cluster and adds `vermeer`, `main_cluster_status` and
`main_cluster_reason`. The separate Vermeer object contains `configured`,
`availability`, `status`, `reason`, observation/freshness fields, Master and
Worker data, and readiness counts. Topology and metric fields are projected
according to the caller's existing Operations capabilities.

## Interpret the state

| Observation | Display |
| --- | --- |
| Connection and all required task streams are ready | Worker ready |
| Registered Worker has an incomplete connection or task stream | Worker not ready, with the failing check |
| No registered Worker | Compute service needs attention |
| Older Master has no cluster API | Readiness unavailable; upgrade the paired image |
| Master request fails after a successful observation | Current state unknown; previous evidence marked stale |
| CPU/RSS sampling fails while task streams remain ready | Worker remains ready; resources unavailable |
| Vermeer is disabled | Not deployed; no impact on overall health |

A configured Vermeer that needs attention can lower an otherwise healthy
deployment overview to **Needs attention**. The UI also shows the main cluster's
own status, so compute-service trouble does not claim that the graph database
is down. A space-scoped Worker availability result is not a global cluster
health verdict.

Worker counts mean **ready / registered**, not expected Helm replicas. A Worker
removed from the registration set is not invented as an offline historical
node. CPU is process CPU time divided by the sampling window, expressed in
cores; memory is process RSS, displayed in binary units. The compute information
panel sums only fresh, available process samples and shows the collected-node
coverage separately for CPU and RSS. Partial coverage is not a complete cluster
resource total. These observations are not host or Pod-limit percentages. The initial CPU observation may report
`metrics_warming_up` until a second sample is available.

## Paired rollout

Build Hubble and Vermeer from the corresponding feature changes, then use the
new image references in the Helm release. The chart's existing immutable image
pins do not automatically refer to unpublished builds. Image publication and
`helm upgrade` are separate release actions; do not substitute an invented tag
or digest. Follow the chart's Vermeer deployment guide for its existing
standalone and distributed layouts.

The browser acceptance screenshots use controlled test responses because the
development workspace does not include a running Server/PD/Store deployment.
They demonstrate the UI interaction, not a live production cluster. The
Vermeer health API is additionally exercised against a local Master/Worker
pair with generated test credentials.
