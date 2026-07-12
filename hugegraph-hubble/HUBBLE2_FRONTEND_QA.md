# Hubble2 frontend QA report

Date: 2026-07-12

Branch: `hubble2` (`d19790b8`)

Browser: Google Chrome, controlled through the Chrome plugin

## Environments

| Mode | Hubble | HugeGraph Server | PD | Store |
|---|---:|---:|---:|---:|
| Standalone | 127.0.0.1:8088 | 127.0.0.1:8080 | disabled | n/a |
| PD | 127.0.0.1:8088 | 127.0.0.1:18080 | 127.0.0.1:18686/18620 | 127.0.0.1:18500 |

The frontend was served from the current source tree at `127.0.0.1:3000`.

## Coverage and results

### Shared and standalone flows

- Login and session refresh: passed with `admin`.
- Navigation, graph management, data source management, data import, graph query,
  graph algorithm, async task management, and profile pages: opened and rendered.
- No application 404 page or desktop horizontal overflow was observed on primary routes.
- Existing graph connection `DEFAULT/hugegraph`: opened successfully.
- Gremlin `g.V().limit(5)`: succeeded and rendered five nodes and two edges.
- Query favorite create/read/delete: succeeded with an underscore-only name.
- Local CSV upload: uploaded `qa_hubble2_people.csv`, created a local-file source,
  completed the four-step mapping wizard, and finished the one-time import as `SUCCEED`.
- Profile password update: changed to a temporary password and restored to `pa`.
- Graph, table, JSON, execution-history, favorite, card/list, search, modal, confirm,
  cancel, and common toolbar controls were exercised where enabled.

### PD-specific flows

- PD login returned the super-admin UI and PD-specific navigation.
- Graph-space list and detail: rendered successfully.
- Graph-space create/read/delete: passed with `qa_hubble2_pd`.
- Account management create/read/delete: passed with `qa_hubble2_user`.
- Account detail/edit/permission actions and graph-space action menus rendered.
- Data, query, algorithm, task, profile, and account routes loaded without application 404.
- Cluster Management, Monitoring, Node Operations, and Alert Management buttons were
  clicked separately. With the configured Dashboard offline, the page correctly reported
  `Dashboard is unavailable. Check dashboard.address and service health.`

## Findings

### P1: invalid profile input triggers the React runtime-error overlay

Steps:

1. Open **My Profile** and click **Edit**.
2. Enter `qa_hubble2_profile` as the account name (longer than the 16-character rule).
3. Click **OK**.

Observed:

- The form displays the expected validation message.
- At the same time, the development runtime overlay reports an uncaught error from
  `handleError` (`[object Object]`).
- The dialog remains open and the user must dismiss the runtime overlay manually.

Expected: invalid input must be handled only by form validation and must not be sent or
raised as an uncaught runtime error.

### Passed with a usability improvement: PD operations availability

On the PD-mode Navigation page, all four operations buttons were tested:

- Cluster Management
- Monitoring
- Node Operations
- Alert Management

The URL stays `/navigation` when the external Dashboard is unavailable, and an error toast
explains the configuration/health problem. The repair pass additionally makes the reason
persistently visible whenever these controls are disabled, rather than relying only on a
transient toast or hover title.

### P2: favorite-name validation disagrees with the backend

Steps:

1. Run a Gremlin query.
2. Favorite it as `qa-hubble2-20260712`.

Observed: the frontend enables submission, then the backend rejects the name and says
only alphanumeric characters and underscores are valid. `qa_hubble2_20260712` succeeds.

Expected: the frontend should enforce `[A-Za-z0-9_]{1,48}` and explain the rule before
submission.

### P2: password rules prevent restoring an existing short password through the UI

The existing development account password was `pa`. After changing it temporarily, the
UI rejected `pa` as a new password because it requires 5-16 characters. The backend still
accepted restoration through the authenticated API. Existing credentials can therefore
be valid for login but impossible to set through the profile UI.

Decision needed: either migrate/enforce the same policy at login/account creation or
allow legacy-password restoration with an explicit compatibility policy.

### P2: source-field names ignore the uploaded CSV header by default

The uploaded file contained `id,name,age`, but the import wizard exposed `col-1`, `col-2`,
and `col-3` because the optional header field was empty. This is functional but easy to
misinterpret; the UI does not clearly explain that the first CSV row is treated as data
unless the header is supplied separately.

### P3: frontend development build emits 52 source-map warnings

The app compiled and ran, but `react-scripts start` emitted 52 warnings, mainly missing
source files from `@antv/x6-react-components`, `dagre-compound`, and unsupported Ant Design
source-map URLs. These warnings obscure actionable compilation problems.

### P3: Chrome logs contain extension communication errors

`Could not establish connection. Receiving end does not exist.` appeared once per route.
The messages originated from the Chrome extension rather than Hubble application code;
they are recorded here but are not classified as a Hubble defect.

## Cleanup

- Favorite test data: deleted.
- Temporary password: restored to `pa`.
- PD account `qa_hubble2_user`: deleted.
- PD graph space `qa_hubble2_pd`: deleted.
- Standalone import task/source used the isolated Hubble test database and should be
  removed during the post-fix regression after restarting standalone mode.

## Repair and regression status

- Invalid profile values now reject with an `Error` object; Chrome regression confirmed
  that only the inline validation message remains and no runtime overlay appears.
- Query favorite submission now remains disabled for backend-incompatible names and shows
  an error input state; underscore-only names remain enabled.
- Disabled PD operation controls now retain a visible availability reason in addition to
  the existing hover title and transient Dashboard health toast.
- Targeted unit/component tests cover the repaired validation and disabled-state behavior.
- `npm run lint` passed. `npm run build` completed successfully with the previously recorded
  third-party source-map warnings.

## Acceptance criteria for the repair pass

1. Invalid profile values remain inside form validation with no uncaught error overlay: passed.
2. Favorite names are rejected client-side using the backend-compatible rule: passed.
3. PD operations entries navigate when healthy or explain Dashboard unavailability: passed.
4. Targeted automated tests cover each repaired behavior: passed.
5. Standalone and PD Chrome regression, including create/read/delete cleanup: passed except
   the isolated standalone task/source cleanup noted above.
6. Frontend lint/build and targeted tests pass before commit: passed.
