# Hubble browser regression guide

## Run the browser fixture checks

The Chromium runner exercises the running frontend with controlled API
responses. It covers authenticated and anonymous visibility, GraphSpace
navigation and editing, graph switching, query notices and counts, delayed
task discovery, result-size guidance and PD diagnostics. It requires the
following frontend changes together:

- [#86](https://github.com/hugegraph/hugegraph-toolchain/pull/86): quiet query
  previews/counts and graph-scoped task discovery (functional revision `656f1bc7`).
- [#87](https://github.com/hugegraph/hugegraph-toolchain/pull/87): actionable PD
  diagnostics and member comparisons (functional revision `7f883baa`).
- [#88](https://github.com/hugegraph/hugegraph-toolchain/pull/88): complete card
  navigation and stale-editor isolation (functional revision `1597ad22`).

Those frontend changes and their later fixes are included in this branch through
the ASF master merges. Run against a frontend built from the same revision as the
runner. The recommended `pnpm verify:ui` entry in `hubble-fe` builds or verifies
production output and manages its preview; the candidate PR CI lane uses that entry once.
The direct runner below remains available for an already running frontend at
`HUBBLE_URL`. See [verification](verification.md) for check selection and evidence.
Fixtures do not establish acceptance of a live PD/Store/Server cluster or replace
the packaged distribution gate.

Install the frontend dependencies and start Hubble's frontend. Then, from
`hugegraph-hubble`, run:

```bash
HUBBLE_URL=http://127.0.0.1:3000 \
HUBBLE_UI_EVIDENCE_DIR=/tmp/hubble-deployment-ui \
  node hubble-dist/assembly/travis/run_ui_deployment_regressions.js
```

The runner accepts `HUBBLE_URL` with or without a trailing slash. It uses
the shared browser smoke helpers. Set `PLAYWRIGHT_NODE_MODULES` when Playwright
is installed outside the frontend dependency directory. Choose an executable
with `--chromium-executable`, `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` or
`CHROME_PATH`; otherwise the helper uses its platform defaults or Playwright's
managed browser, without forcing an unrelated system Chromium.

Keep the output directory outside Git. Each invocation replaces the previous
`browser-report.json` and known scenario/failure screenshots while preserving
unrelated files. Startup failures produce a fresh failed report; a child that
exits without a report is not treated as successful. Diagnostic screenshots
include context and page indices so one page cannot overwrite another. Screenshots
disable animations and use settled UI state rather than a fixed sleep. The header
whitespace check intentionally targets the noninteractive card hit area; other
controls prefer accessible roles, visible text and stable form placeholders.

Task fixtures accept submission, lookup, list and result requests only for
`soc_graph/hugegraph` and task `80001`. Lookups must request that exact ID, and the
submission must contain the scenario's query. Mismatched scope, IDs, methods or
submission content are recorded as unexpected requests and return no task data;
a stale-context request cannot turn this scenario green.

Retain run reports, screenshots and performance samples in external CI artifacts
or PR attachments. Record the actual frontend revision, runner revision and
environment with that evidence. Do not commit one-time outputs or infer that a
fixture run verifies a production deployment.

## Understand GraphSpace quota data

Hubble calls the Server GraphSpace API. The Server profile endpoint refreshes
**used storage** from PD but obtains **configuration and storage limits** from
its local GraphSpace cache. Utilization combines these values. Stable used bytes
with alternating limits can indicate inconsistent Server caches; identify the
responding Server before attributing a deployment issue to the frontend.

Hubble must also ignore stale detail responses after an editor closes or switches
spaces. That protects the current form but cannot repair inconsistent Server
replicas. GraphSpace and Schema use distinct caches; broader synchronization
changes require separate coverage.

## Read PD health diagnostics

`UP` describes node registration/availability. Missing system metrics do not
prove that an online PD member is down. `Cluster_Not_Ready` is a separate
readiness signal and must be considered separately from collection status.

The diagnostic page compares the configured PD origin with member REST origins,
including origin and base-path matching. No match and multiple matches require
different diagnosis. The visible member list is bounded; counts use the complete
deduplicated set. Failed Server or Store requests retain their owning node,
metric group, request path and HTTP status without exposing credentials, URL
parameters or base-path contents.

Check `pd.server` in `hugegraph-hubble.properties` and `server.protocol` when no
scheme is specified. A load balancer or proxy address may not identify a single
member. Use a reachable, correctly advertised member endpoint or correct the
member's advertised `restUrl`. Restart Hubble after changing its configuration
and refresh the diagnostic page. Collection warnings alone do not identify the
actual endpoint configuration or repair it automatically.

## Diagnose asynchronous result limits

Asynchronous execution does not bypass Server's per-graph
`task.result_size_limit`. Its default is 16 MiB for compressed stored task
results. Reduce result rows or project fewer properties when a task exceeds that
limit. A failed task is not a successful full result; preserve its exact graph
and task ID when inspecting the failure or retrying discovery.

## Configure Vermeer load permissions

Token-authenticated HugeGraph load/reload requires a Vermeer administrator
service token. Configure `vermeer.token_file` as described in the
[Vermeer development guide](vermeer-development.md). Other upstream response
bodies must remain undisclosed. An anonymous-mode fixture does not establish
token-authenticated load/reload acceptance.

## Plan live deployment verification

Exercise authentication, anonymous access, graph switching and mutation recovery
against the intended deployment separately from the mocked browser runner.
Include PD discovery and multiple Server instances when investigating cache or
health problems. Removing a graph item from the canvas is a view operation;
verify persistent deletion through the data API when that is the intended action.

For rendering comparisons, pin both frontend revisions and the runner, use the
same fixtures and browser, and verify the served source. Separate unmeasured
warm-up queries from timed samples and interleave baseline/candidate runs to
reduce order effects. Small development-UI samples do not establish production
throughput or tail-latency guarantees.

## Collect graph-card counts and diagnose Vermeer result reads

Click an uncollected graph-card count to request the existing bounded Server
statistics. Duplicate clicks share the in-flight guard. Failed collection leaves
a retry action while overview and query navigation remain usable.

For an authorized native Vermeer deployment with ready workers, the count source
menu also offers **Graph compute statistics (loaded snapshot)**. Hubble verifies
the caller's graph read permission and existing Vermeer administrator boundary,
then reads only the selected loaded graph's total counters. The visible count
label and tooltip identify this source and snapshot time. These counts may lag
Server writes; refresh the graph through OLAP loading when new data is needed.
No full Server graph scan, graph load or compute task is started by this read.

The additive `GET /graphspaces/{space}/graphs/{graph}/statistics/vermeer` endpoint
returns `vertex_count`, `edge_count`, `update_time`, `source: vermeer` and
`approximate: false`. Unloaded graphs and unavailable workers are rejected. The
existing statistics endpoint retains its behavior. The `/vermeer` capability
response advertises `statistics_available` only for ready native integration;
legacy Dashboard availability does not enable this new source.

A successful Vermeer task can have unavailable result values. Both exact native
absence responses (`task_id error:<id>` and `compute value not exist, task_id:<id>`)
retain successful task metadata, while mismatched IDs, authorization errors and
transient failures remain errors. Unexpected result-read failures show a single
inline reason, task scope and retry guidance. They do not change the computation
status to failed. Result state resets when the signed-in identity changes.

## Switch between OLTP subgraph queries and OLAP whole-graph compute

Open `algorithms/<graphspace>/<graph>`. The initial OLTP tab contains only
interactive traversers and links to their REST API documentation. Select the
OLAP tab to browse Vermeer whole-graph algorithms and their documentation.
General instructions and setup links belong to the idle canvas; the sidebar
retains search, algorithm entries and parameter reset. Help sits beside each
algorithm name and supports both hover and keyboard focus.

The OLAP tab owns the graph-loading entry. An unloaded graph explains that it
must be loaded before computation; hover over **Load for graph compute** to
identify the Vermeer engine. Query and async-task pages do not duplicate that
loading control. Start or retry loading on the OLAP tab and inspect progress in
async tasks. Switching back to OLTP does not cancel an acknowledged load.

For browser verification, exercise both authenticated and anonymous PD modes:

1. Open an explicit graph route and verify that only the OLTP catalog is shown.
2. Select OLAP; verify the unloaded-state guidance, loading button and tooltip.
3. Submit one load and verify its graphspace/graph scope and pending state.
4. Return to OLTP and verify that the loading button is absent.
5. Switch GraphSpace and verify the selected graph and account visibility.
6. Delay or fail Vermeer discovery: OLTP remains usable, while Computer entries
   remain hidden until discovery is confirmed. Retryable worker failures must
   not briefly expose a different execution path before automatic recovery.
7. Confirm Vermeer is disabled and Computer is unavailable: the canvas shows
   startup guidance with Vermeer and Helm documentation. HTTP errors and malformed
   capabilities instead show a retryable check failure, without claiming missing
   configuration. Switching tabs preserves a confirmed capability; switching
   graph or identity ignores stale responses.
8. Keep existing results visible when changing tabs; guidance only replaces an
   idle, empty canvas. Check help placement and focus at desktop and 768px widths.

An intercepted API run validates this navigation and feedback contract. It does
not validate actual Vermeer readiness, graph loading or algorithm correctness.
