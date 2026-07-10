# Hubble API Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove unreleased Hubble graph-management legacy facades, align active calls with Server PR #3008, retain the transitional clear actions with strong confirmation, and deprecate the unsupported public Client per-graph reload API.

**Architecture:** Hubble frontend calls a small REST facade using resource-oriented methods. Hubble backend translates its compact graph-update body through `GraphsService` and `hugegraph-client` into Server's action envelope. Destructive clear actions remain behind a reusable controlled confirmation component until Server exposes two canonical clear operations.

**Tech Stack:** Java 11, Spring MVC/MockMvc, HugeGraph Java Client, React 18, Ant Design 4, Jest/Testing Library, Maven.

## Global Constraints

- Hubble 2.0 legacy facade routes are removed, not deprecated.
- Public `hugegraph-client` per-graph reload methods are deprecated, not deleted.
- Both existing graph-clear actions remain available in this change.
- The data-only path must carry a precise follow-up TODO and must not claim a verified schema-preservation guarantee.
- Existing unrelated workspace changes and generated artifacts must not be staged.

---

### Task 1: Canonical graph update, create, and default routes

**Files:**
- Create: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/entity/graphs/GraphUpdateEntity.java`
- Modify: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/graphs/GraphsController.java`
- Modify: `hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/GraphsControllerCanonicalTest.java`

**Interfaces:**
- Consumes: JSON `{"nickname":"..."}` at `PUT /api/v1.3/graphspaces/{graphspace}/graphs/{graph}`.
- Produces: canonical JSON create, update, default set/unset/read routes with no legacy GET mutations or form-create route.

- [ ] **Step 1: Replace the legacy-positive tests with failing canonical and route-removal tests**

Add MockMvc tests that use `put(...)`, `get(...)`, and status assertions:

```java
@Test
public void testCanonicalUpdateGraphUsesJsonBody() throws Exception {
    this.mvc.perform(put("/api/v1.3/graphspaces/DEFAULT/graphs/graph_a")
                     .with(this.withClient)
                     .contentType(MediaType.APPLICATION_JSON)
                     .content("{\"nickname\":\"GraphNick\"}"))
            .andExpect(status().isOk());
    Mockito.verify(this.graphsService)
           .update(this.client, "GraphNick", "graph_a");
}
```

Also assert `GET /graph_a/update`, `GET /graph_a/setdefault`,
`GET /graph_a/unsetdefault`, and form `POST /graphs` are not successful; add
`GET /graphs/default` delegation coverage. Remove the test that expects legacy
form creation to succeed.

- [ ] **Step 2: Run the focused backend test and verify RED**

Run:

```bash
j11 >/dev/null && mvn test -P unit-test -pl hugegraph-hubble/hubble-be \
  -Dtest=GraphsControllerCanonicalTest -ntp
```

Expected: FAIL because PUT update and canonical default read do not exist and legacy routes still succeed.

- [ ] **Step 3: Add the compact update entity and canonical controller mappings**

Create:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GraphUpdateEntity {
    private String nickname;
}
```

Change the controller to:

```java
@PutMapping("{graph}")
public void update(@PathVariable("graphspace") String graphspace,
                   @PathVariable("graph") String graph,
                   @RequestBody GraphUpdateEntity request) {
    this.graphsService.update(this.authClient(graphspace, null),
                              request.getNickname(), graph);
}

@GetMapping("default")
public Map<String, String> getDefault(
        @PathVariable("graphspace") String graphspace) {
    return this.graphsService.getDefault(this.authClient(graphspace, null));
}
```

Delete the form-urlencoded create method, GET update, GET set/unset default,
and `getdefault` mapping. Keep the existing canonical create, POST default, and
DELETE default methods, calling the service directly instead of removed alias methods.

- [ ] **Step 4: Run the focused backend test and verify GREEN**

Run the Step 2 command. Expected: all `GraphsControllerCanonicalTest` tests pass.

- [ ] **Step 5: Commit the graph facade contract slice**

```bash
git add hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/graphs/GraphsController.java \
  hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/entity/graphs/GraphUpdateEntity.java \
  hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/GraphsControllerCanonicalTest.java
git commit -m "refactor(hubble): align graph facade routes" \
  -m $'- replace GET graph update with JSON PUT\n- remove form graph creation facade\n- retain canonical default graph resources\n- cover removed aliases with MockMvc tests'
```

### Task 2: Remove unused default-role and default-GraphSpace facades

**Files:**
- Modify: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/auth/RoleController.java`
- Modify: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/space/GraphSpaceController.java`
- Modify: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/service/space/GraphSpaceService.java`
- Create: `hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/LegacyFacadeRemovalTest.java`
- Modify: `hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/UnitTestSuite.java`

**Interfaces:**
- Consumes: Spring mapping annotations on Hubble controllers.
- Produces: no Hubble routes named `setdefaultrole`, `deldefaultrole`, `setdefault`, or `getdefault`.

- [ ] **Step 1: Write a failing reflection-based route-removal test**

The test iterates declared controller methods and their `GetMapping` /
`DeleteMapping` values, asserting the obsolete mapping names are absent. It
also asserts `GraphSpaceService` has no public `setdefault` or `getdefault`
methods. Add the test to `UnitTestSuite`.

- [ ] **Step 2: Run the test and verify RED**

```bash
j11 >/dev/null && mvn test -P unit-test -pl hugegraph-hubble/hubble-be \
  -Dtest=LegacyFacadeRemovalTest -ntp
```

Expected: FAIL listing the four existing facade methods.

- [ ] **Step 3: Remove the obsolete controller and service methods**

Delete `RoleController.setDefaultRole`, `RoleController.delDefaultRole`,
`GraphSpaceController.setdefault`, `GraphSpaceController.getdefault`,
`GraphSpaceService.setdefault`, and `GraphSpaceService.getdefault`. Remove
imports made unused by those deletions.

- [ ] **Step 4: Run the test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit the obsolete facade removal**

```bash
git add hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/auth/RoleController.java \
  hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/controller/space/GraphSpaceController.java \
  hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/service/space/GraphSpaceService.java \
  hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/LegacyFacadeRemovalTest.java \
  hugegraph-hubble/hubble-be/src/test/java/org/apache/hugegraph/unit/UnitTestSuite.java
git commit -m "refactor(hubble): remove unused default facades" \
  -m $'- remove noncanonical default-role routes\n- remove Hubble-only default GraphSpace routes\n- delete unused service pass-through methods\n- add route-removal regression coverage'
```

### Task 3: Align frontend API calls and remove default-GraphSpace UI

**Files:**
- Modify: `hugegraph-hubble/hubble-fe/src/api/manage.js`
- Create: `hugegraph-hubble/hubble-fe/src/api/manage-contract.test.js`
- Modify: `hugegraph-hubble/hubble-fe/src/pages/GraphSpace/index.js`

**Interfaces:**
- Consumes: the canonical Hubble backend routes from Tasks 1 and 2.
- Produces: frontend API functions using PUT JSON and `/graphs/default`, with no default-GraphSpace mutation exports.

- [ ] **Step 1: Write failing request-contract tests**

Mock `./request`, load `manage.js`, and assert:

```javascript
manage.updateGraph('DEFAULT', 'g', {nickname: 'nick'});
expect(request.put).toHaveBeenCalledWith(
    '/graphspaces/DEFAULT/graphs/g',
    {nickname: 'nick'}
);

manage.getDefaultGraph('DEFAULT');
expect(request.get).toHaveBeenCalledWith(
    'graphspaces/DEFAULT/graphs/default'
);

expect(manage.setDefaultGraphSpace).toBeUndefined();
expect(manage.getDefaultGraphSpace).toBeUndefined();
```

- [ ] **Step 2: Run the frontend contract test and verify RED**

```bash
CI=true npm test -- --runInBand src/api/manage-contract.test.js
```

Expected: FAIL because update uses GET, default read uses `getdefault`, and default-GraphSpace functions are exported.

- [ ] **Step 3: Implement the frontend contract and UI removal**

Use `request.put(path, params)` for graph update and switch default graph read
to `/graphs/default`. Remove `setDefaultGraphSpace` and
`getDefaultGraphSpace` definitions/exports. Remove the `setDefault`,
`handleSetDefault`, and “设为默认” GraphSpace action from `GraphSpace/index.js`.
Keep graph rows' server-provided `default` display/guard behavior unchanged.

- [ ] **Step 4: Run the focused test and production build**

```bash
CI=true npm test -- --runInBand src/api/manage-contract.test.js
npm run build
```

Expected: test PASS and build exits 0, proving no deleted export is still referenced.

- [ ] **Step 5: Commit the frontend contract slice**

```bash
git add hugegraph-hubble/hubble-fe/src/api/manage.js \
  hugegraph-hubble/hubble-fe/src/api/manage-contract.test.js \
  hugegraph-hubble/hubble-fe/src/pages/GraphSpace/index.js
git commit -m "refactor(hubble): use canonical graph api calls" \
  -m $'- send graph updates with PUT JSON\n- read the canonical default graph resource\n- remove default GraphSpace facade calls\n- hide unsupported default GraphSpace mutation'
```

### Task 4: Add strong graph-clear confirmation

**Files:**
- Create: `hugegraph-hubble/hubble-fe/src/pages/Graph/ClearGraphConfirmModal.js`
- Create: `hugegraph-hubble/hubble-fe/src/pages/Graph/ClearGraphConfirmModal.test.js`
- Modify: `hugegraph-hubble/hubble-fe/src/pages/Graph/index.js`
- Modify: `hugegraph-hubble/hubble-fe/src/i18n/resources/zh-CN/modules/pages.json`
- Modify: `hugegraph-hubble/hubble-fe/src/i18n/resources/en-US/modules/pages.json`
- Modify: `hugegraph-hubble/hubble-fe/src/api/manage.js`
- Modify: `hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/service/graphs/GraphsService.java`

**Interfaces:**
- Consumes: `open`, `graphspace`, `graph`, `mode`, `onCancel`, `onSuccess`, and async `onConfirm` props.
- Produces: a controlled modal that resolves destructive requests only after exact graph-name confirmation.

- [ ] **Step 1: Write failing component tests**

Using Testing Library, cover both modes. Assert GraphSpace, graph name, deletion
scope, irreversible warning, and the transitional schema warning are visible.
Type a wrong graph name and assert the confirm button stays disabled; type the
exact name and assert it enables. Resolve `onConfirm` with `{status: 200}` and
assert `onSuccess` is called. Resolve with `{status: 500, message: 'boom'}` and
assert the dialog remains with `boom`. Reject with `new Error('network')` and
assert the dialog remains usable.

- [ ] **Step 2: Run the component test and verify RED**

```bash
CI=true npm test -- --runInBand \
  src/pages/Graph/ClearGraphConfirmModal.test.js
```

Expected: FAIL because the component does not exist.

- [ ] **Step 3: Implement the reusable controlled modal**

Use Ant Design `Modal`, `Input`, `Alert`, and `Typography`. Maintain local
confirmation text, pending state, and error state. Reset state when the dialog
opens for a new graph/mode. Set `okButtonProps.disabled` unless the typed value
exactly equals `graph`; set `confirmLoading` while awaiting `onConfirm`.
Translate all user-facing content with new `graph.clear_confirm.*` keys.

- [ ] **Step 4: Integrate the modal and add precise transitional TODOs**

Replace both `Modal.confirm` calls in `Graph/index.js` with one
`ClearGraphConfirmModal`. Store `{graph, mode}` selection; pass
`clearGraphData` or `clearGraphDataAndSchema` as `onConfirm`; refresh only via
`onSuccess`.

Immediately above the data-only frontend request and the data-only branch in
`GraphsService.truncate`, add:

```text
TODO: Replace this temporary facade after Server exposes and verifies a
data-only clear operation that preserves schema.
```

Do not change the current `/truncate` HTTP method or service execution in this task.

- [ ] **Step 5: Run component tests and frontend build**

```bash
CI=true npm test -- --runInBand \
  src/pages/Graph/ClearGraphConfirmModal.test.js \
  src/api/manage-contract.test.js
npm run build
```

Expected: all focused tests PASS and build exits 0.

- [ ] **Step 6: Commit the clear-safety slice**

```bash
git add hugegraph-hubble/hubble-fe/src/pages/Graph/ClearGraphConfirmModal.js \
  hugegraph-hubble/hubble-fe/src/pages/Graph/ClearGraphConfirmModal.test.js \
  hugegraph-hubble/hubble-fe/src/pages/Graph/index.js \
  hugegraph-hubble/hubble-fe/src/api/manage.js \
  hugegraph-hubble/hubble-fe/src/i18n/resources/zh-CN/modules/pages.json \
  hugegraph-hubble/hubble-fe/src/i18n/resources/en-US/modules/pages.json \
  hugegraph-hubble/hubble-be/src/main/java/org/apache/hugegraph/service/graphs/GraphsService.java
git commit -m "feat(hubble): require strong graph clear confirmation" \
  -m $'- show graph and deletion scope\n- require exact graph-name confirmation\n- retain errors without closing the dialog\n- document temporary data-only clear semantics'
```

### Task 5: Deprecate unsupported Client per-graph reload

**Files:**
- Modify: `hugegraph-client/src/main/java/org/apache/hugegraph/api/graphs/GraphsAPI.java`
- Modify: `hugegraph-client/src/main/java/org/apache/hugegraph/driver/GraphsManager.java`
- Modify: `hugegraph-client/src/test/java/org/apache/hugegraph/unit/GraphsAPITest.java`

**Interfaces:**
- Consumes: public per-graph reload methods retained for compatibility.
- Produces: runtime-visible `@Deprecated` markers and Javadocs directing users to no-argument whole-server reload.

- [ ] **Step 1: Write a failing deprecation reflection test**

```java
Assert.assertNotNull(GraphsAPI.class.getMethod("reload", String.class)
                                    .getAnnotation(Deprecated.class));
Assert.assertNotNull(GraphsManager.class.getMethod("reload", String.class)
                                        .getAnnotation(Deprecated.class));
Assert.assertNull(GraphsAPI.class.getMethod("reload")
                                 .getAnnotation(Deprecated.class));
```

- [ ] **Step 2: Run the client unit test and verify RED**

```bash
j11 >/dev/null && mvn test -pl hugegraph-client \
  -Dtest=GraphsAPITest -Dmaven.javadoc.skip=true -ntp
```

Expected: FAIL because the per-graph methods are not deprecated.

- [ ] **Step 3: Add deprecation annotations and migration Javadocs**

Mark only `reload(String)` in `GraphsAPI` and `GraphsManager` as deprecated.
State that current Server per-graph management accepts only update and that
callers should use `reload()` when whole-server graph reload is intended.

- [ ] **Step 4: Run the client unit test and verify GREEN**

Run the Step 2 command. Expected: PASS.

- [ ] **Step 5: Commit the Client compatibility slice**

```bash
git add hugegraph-client/src/main/java/org/apache/hugegraph/api/graphs/GraphsAPI.java \
  hugegraph-client/src/main/java/org/apache/hugegraph/driver/GraphsManager.java \
  hugegraph-client/src/test/java/org/apache/hugegraph/unit/GraphsAPITest.java
git commit -m "refactor(client): deprecate per-graph reload" \
  -m $'- retain public methods for compatibility\n- mark unsupported per-graph reload deprecated\n- document whole-server reload alternative\n- add annotation regression coverage'
```

### Task 6: Integrated verification and scope audit

**Files:**
- Verify only; modify earlier files only if a verification failure identifies a task-scoped defect.

**Interfaces:**
- Consumes: all prior task outputs.
- Produces: fresh evidence for tests, build, route removal, and clean scope.

- [ ] **Step 1: Run backend focused suites with Java 11**

```bash
j11 >/dev/null && mvn test -P unit-test -pl hugegraph-hubble/hubble-be \
  -Dtest=GraphsControllerCanonicalTest,LegacyFacadeRemovalTest -ntp
```

- [ ] **Step 2: Run client focused tests**

```bash
j11 >/dev/null && mvn test -pl hugegraph-client \
  -Dtest=GraphsAPITest -Dmaven.javadoc.skip=true -ntp
```

- [ ] **Step 3: Run all frontend unit tests and production build**

```bash
CI=true npm test -- --runInBand
npm run build
```

- [ ] **Step 4: Audit removed routes and unintended changes**

```bash
rg -n "setdefaultrole|deldefaultrole|graphs/\$\{graph\}/update|graphs/getdefault|setDefaultGraphSpace|getDefaultGraphSpace" \
  hugegraph-hubble/hubble-be/src hugegraph-hubble/hubble-fe/src
git diff --check HEAD~5..HEAD
git status --short
git diff --stat f30ab855..HEAD
```

Expected: route search has no product-code hits, diff check is empty, and only
task files plus the two design/plan documents differ from the base commit.

- [ ] **Step 5: Review requirement coverage**

Confirm every accepted item is represented: PUT JSON update; legacy form create
removal; canonical default graph read/mutations; default-role facade removal;
per-graph reload deprecation; hidden default-GraphSpace mutation; retained
clear operations; precise data-only TODO; strong confirmation; fresh test and
build evidence.
