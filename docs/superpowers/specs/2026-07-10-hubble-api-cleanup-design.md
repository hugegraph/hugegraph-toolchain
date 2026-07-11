# Hubble API Cleanup Design

> **Status: Accepted.** 本文只记录稳定的 API/兼容性决策，不记录当前进度。
> 未完成事项以 [Hubble2 TODOs](../../../.codex-task/hubble2-hardening/TODOs.md) 为准。

## Goal

Align Hubble's graph-management facade with the canonical HugeGraph Server
contracts from `apache/hugegraph#3008`, remove unreleased legacy endpoints, and
make destructive graph-clear actions explicit and difficult to trigger by
mistake.

## Compatibility Boundary

Hubble 2.0 is unreleased, so its frontend and backend facade routes do not need
legacy compatibility. Obsolete Hubble-only routes are removed instead of kept
as aliases.

`hugegraph-client` is a public library with a different compatibility boundary.
Its unsupported per-graph reload methods remain binary/source compatible for
this release but are marked `@Deprecated` with guidance that only whole-server
graph reload is currently supported. The no-argument reload methods remain.

## Canonical Hubble Graph APIs

### Update graph metadata

The frontend sends a JSON mutation to the graph resource:

```http
PUT /api/v1.3/graphspaces/{graphspace}/graphs/{graph}
Content-Type: application/json

{"nickname":"New nickname"}
```

The backend validates the request shape and calls
`GraphsService.update(...)`. The client layer continues to translate that call
to Server's `{"action":"update","update":{...}}` body. The GET
`/{graph}/update` facade is removed.

### Create graph

Only path-resource JSON creation remains:

```http
POST /api/v1.3/graphspaces/{graphspace}/graphs/{graph}
Content-Type: application/json

{"nickname":"Graph nickname","schema":"optional-template"}
```

The form-urlencoded collection endpoint `POST /graphs` and its legacy test are
removed.

### Default graph

Hubble exposes only these routes:

```text
POST   /api/v1.3/graphspaces/{graphspace}/graphs/{graph}/default
DELETE /api/v1.3/graphspaces/{graphspace}/graphs/{graph}/default
GET    /api/v1.3/graphspaces/{graphspace}/graphs/default
```

GET mutation aliases `setdefault` and `unsetdefault` are removed. The old
`getdefault` read alias is replaced by the canonical `default` resource.

## Removed Unused Facades

The unused role routes `setdefaultrole` and `deldefaultrole` are removed.
Hubble will expose default-role management only when a product flow needs it,
using Server's canonical POST/GET/DELETE resource contract.

The Hubble-only default-GraphSpace `setdefault` and `getdefault` routes, service
methods, frontend API functions, and UI mutation are removed. Non-PD routing
continues to use the existing `DEFAULT` GraphSpace product-mode rule. Hubble
does not invent a default-GraphSpace contract before Server defines one.

## Graph Clear Transitional Behavior

Both existing user-facing clear actions and the GET `/{graph}/truncate` Hubble
facade remain temporarily because Server has not implemented two distinct
canonical operations.

The data-only frontend/backend path receives a code TODO that states the exact
follow-up: replace the temporary facade after Server provides and verifies a
data-only operation that preserves schema. The UI must not silently claim that
the current Server behavior has that guarantee; it warns users to treat the
operation as potentially affecting schema until the Server contract is ready.

### Strong confirmation dialog

Both actions use one controlled confirmation component with a mode of
`data` or `schema-and-data`.

The dialog always:

- shows the GraphSpace and graph name;
- describes the requested deletion scope;
- states that the operation is irreversible;
- requires the exact graph name before enabling confirmation;
- disables duplicate submission while the request is pending;
- preserves the dialog and typed value on failure, showing the backend error;
- closes, reports success, and refreshes the graph list only after success.

For `data`, the dialog describes the intended vertex/edge data deletion and
adds the transitional warning that schema preservation is not yet guaranteed
by a dedicated Server API. For `schema-and-data`, it explicitly lists graph
data, property keys, vertex labels, edge labels, and indexes.

## Error Handling

Existing response normalization remains the transport boundary. API failures
must use the backend-provided message when available. A failed destructive
request must not close the dialog or clear the user's confirmation input.
Success handling must not depend on a response body when the backend returns an
empty successful response.

## Dashboard Address and Health Boundary

`dashboard.address` remains the shared Vermeer/Dashboard host-and-port value;
it does not contain a scheme, path, credentials, query, or fragment. The
existing `server.protocol` option supplies `http` or `https`. Hubble's
`GET /api/v1.3/dashboard` returns both values after configuration validation
and performs no outbound health request.

The browser constructs the external origin from those two fields. It does not
probe the configured address during page load. A reachability probe is allowed
only after the user explicitly clicks a Dashboard operation, uses no
credentials or referrer, has a bounded timeout, and either opens the requested
external page or reports one localized actionable failure. This preserves
private and loopback deployment support without turning passive navigation
rendering into an arbitrary network request.

## Retired Role Authorization Deep Link

Role management has no supported Navigation entry in Hubble 2.0, and the
top-level `/role` path already redirects to `/navigation`. The orphaned legacy
`/role/graphspace/:graphspace/:role` authorization path follows the same
replace-redirect policy instead of rendering the old RoleAuth page. This keeps
direct bookmarks from exposing a partially localized, unsupported product
surface while preserving a deterministic non-404 recovery path.

## Retired and External Operational APIs

The unused graph-storage facade is retired. Hubble has no reachable product
consumer for `/graphspaces/{graphspace}/graphs/{graph}/storage`, and the former
backend implementation was already commented out. Both remnants are removed;
Hubble must not invent storage data to keep a dead endpoint green.

Audit log is not a Hubble 2.0 published surface: there is no route, navigation
entry or frontend client, and its Elasticsearch dependency is disabled by
default. The fully commented controller is removed instead of restoring an
untested capability. Reintroducing audit requires a separate accepted design,
real Elasticsearch environment and complete authentication/browser evidence.

PD and HStore status endpoints remain external-capability boundaries. Server
1.7.0 does not expose the REST paths required by the current HugeClient calls.
Hubble keeps the real calls and records the exact dependency at each call site;
it must not synthesize a healthy response. These endpoints are not consumed by
the Hubble frontend, whose monitoring entry opens Dashboard.

The missing APIs remain external blockers, but their failure representation is
Hubble-owned. These two status endpoints map the verified downstream
`ServerException` to HTTP/business 503 with a localized actionable message and
no downstream cause. Other ServerException paths retain their legacy HTTP
compatibility until separately migrated.

In non-PD mode, `/graphspaces/list` exposes `DEFAULT` for business navigation,
while the paginated `/graphspaces` management API has no manageable records.
This is an intentional capability distinction, not evidence that a synthetic
GraphSpace record should enable unsupported management mutations.

Graph nickname input follows the Server contract rather than the card display
truncation. Server master `3bd990d8` defines a 48-character maximum in
`GraphManager.NICKNAME_MAX_LENGTH`; Hubble therefore accepts up to 48
characters while cards may still visually truncate long nicknames. The graph
name remains immutable during edit and follows its separate name contract.

## Testing and Acceptance

Backend MockMvc coverage verifies:

- PUT graph update accepts JSON and delegates the path graph plus nickname;
- GET `/{graph}/update` is absent;
- form-urlencoded `POST /graphs` is absent while JSON path creation works;
- canonical default POST, DELETE, and GET routes delegate correctly;
- legacy default graph GET aliases are absent;
- obsolete role and default-GraphSpace routes are absent.

Frontend unit coverage verifies:

- graph update uses PUT with a JSON body;
- default graph read uses `/graphs/default`;
- removed facade functions are no longer exported;
- destructive confirmation text contains GraphSpace, graph name, scope, and
  irreversible warning;
- confirmation is disabled until the exact graph name is entered;
- each mode calls only its matching API;
- failures keep the dialog open and expose the backend message.

Client coverage verifies the public per-graph reload methods are deprecated at
compile/API level while no-argument reload behavior remains unchanged.

Final verification uses Java 11 for Hubble backend tests, the focused frontend
test suites, frontend production build, and targeted client tests/compile.

## Out of Scope

- Defining or implementing Server's two graph-clear APIs.
- Claiming schema preservation before that Server behavior is verified.
- Creating a new default-role product flow.
- Defining a canonical default-GraphSpace Server API.
- Removing the public per-graph reload methods in this release.
