# Apache HugeGraph-Hubble

[![License](https://img.shields.io/badge/license-Apache%202-0E78BA.svg)](https://www.apache.org/licenses/LICENSE-2.0.html)
[![hugegraph-hubble-ci](https://github.com/apache/hugegraph-toolchain/actions/workflows/hubble-ci.yml/badge.svg?branch=master)](https://github.com/apache/hugegraph-toolchain/actions/workflows/hubble-ci.yml)
[![CodeQL](https://github.com/apache/hugegraph-toolchain/actions/workflows/codeql-analysis.yml/badge.svg)](https://github.com/apache/hugegraph-toolchain/actions/workflows/codeql-analysis.yml)

hugegraph-hubble is a graph management and analysis platform that provides features:
graph data load, schema management, graph relationship analysis, and graphical display.

## Product Tour

### Graph Data Workbench

Start from one workspace for graph discovery, import, query, account management, and cluster operations.

![Hubble graph data workbench](docs/images/showcase/home-workbench.jpg)

### GQL Traversal

Run Gremlin queries and inspect their graph, table, or JSON results without leaving the workbench.

![Hubble GQL traversal and graph visualization](docs/images/showcase/gql-traversal.jpg)

### Schema Templates

Prepare reusable schemas before configuring data sources and import tasks.

![Hubble schema template management](docs/images/showcase/schema-templates.jpg)

### Asynchronous Tasks

Track asynchronous queries and algorithms, then expand compact results inline.

![Hubble asynchronous task results](docs/images/showcase/async-tasks.jpg)

## Authentication, connections, and compatibility

Hubble uses one capability-driven connection boundary for `1.8/master`. Authentication mode is detected from the connected HugeGraph Server; Hubble does not maintain a separate authentication switch. Account and permission entry points are hidden when Server allows anonymous access. Connection switching always goes through the backend resolver. In PD mode, a valid server address returned by discovery is sufficient; a manually configured server URL is not required.

The UI presents four stable permission meanings: super administrator, GraphSpace read-only, GraphSpace read-write, and GraphSpace administrator. The last one means member management plus read/write within that GraphSpace; low-level `role`, `target`, `access`, and `belong` fields are not exposed.

The compatibility boundary is deliberately small:

| HugeGraph Server / PD | Deployment | Hubble compatibility | Scope and limitations |
|---|---|---|---|
| Server 1.5.x | Standalone, normally without authentication | Minimum compatibility | Basic graph, schema, data, and Gremlin workflows only. GraphSpace, account permissions, PD/Store topology, cluster operations, and newer algorithms are unavailable. |
| Server 1.7.x with matching PD/Store 1.7.x | Standalone or distributed | Minimum compatibility through legacy adapters | Core management and query workflows remain usable, but legacy REST/Gremlin authentication, permission semantics, metrics, and algorithm capabilities may provide a reduced experience. |
| Server, PD, and Store 1.8.x or later | Distributed deployment recommended | Full and recommended experience | Current GraphSpace, account permission presets, cluster operations, async tasks, and algorithm capability handling are designed and validated against this generation. |

Use matching Server, PD, and Store minor versions in a distributed cluster. **Server/PD 1.8 or later is strongly recommended for the best Hubble experience.** Support for 1.5 and 1.7 is intentionally limited to minimum usability and does not imply feature parity with the current release. Version checks stay in the client adapter/resolver rather than being scattered through controllers or pages. See [`AGENTS.md`](AGENTS.md) for verification rules.

## Local development feedback loop

Run the frontend with third-party source-map noise disabled:

```bash
cd hubble-fe
yarn dev
```

Run the backend incrementally with Java 17 and the Maven daemon:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
mvnd -pl hubble-be -DskipTests compile dependency:build-classpath \
  -Dmdep.outputFile=/tmp/hubble-be-classpath
mkdir -p /tmp/hubble-dev-home
cd hubble-be
"$JAVA_HOME/bin/java" -Dfile.encoding=UTF-8 \
  --add-opens=java.base/java.net=ALL-UNNAMED \
  -Dhubble.home.path=/tmp/hubble-dev-home \
  -cp "target/classes:$(</tmp/hubble-be-classpath)" \
  org.apache.hugegraph.HugeGraphHubble
```

Stop the previous backend process before restarting after Java changes. Changes
under `hubble-be/src/main/resources` are copied by the next `compile`. The POM
does not configure `spring-boot:run`, so that command is not a supported local
shortcut. Build
the release package with `mvnd package -DskipTests`; these development commands
do not change packaged runtime behavior.

Native Store metrics use an operator-managed exact-origin allowlist in addition
to PD topology and metrics-target discovery. The packaged default
`operations.store.allowed_targets=[http://127.0.0.1:8520,http://[::1]:8520]`
is only for local testing. Production deployments must explicitly list every
trusted Store scheme, hostname or literal address, and port; discovery cannot
add origins to this allowlist. HTTPS origins keep their configured hostname for
TLS SNI and certificate hostname verification.

## Functional Modules Overview

```mermaid
graph TD
    Hubble["HugeGraph-Hubble Platform"]

    Hubble --> Conn["1. Workspace Management<br>(Multi-Graph Connections)"]
    Hubble --> Schema["2. Visual Schema Designer<br>(Vertex, Edge & Index Types)"]
    Hubble --> Load["3. Guided Data Importer<br>(Source Mapping & Task Monitor)"]
    Hubble --> Analyze["4. Graph Analysis & Visualization<br>(Gremlin Console & Visual Exploration)"]
    Hubble --> System["5. System Administration<br>(Async Tasks & Access Control)"]
```

<details>
<summary>ASCII diagram (for terminals/editors)</summary>

```
             ┌──────────────────────────────────────────┐
             │       HugeGraph-Hubble Platform          │
             └────────────────────┬─────────────────────┘
                                  │
      ┌───────────────────────────┼───────────────────────────┐
      ▼                           ▼                           ▼
┌──────────────┐            ┌──────────────┐            ┌──────────────┐
│  Workspace   │            │Visual Schema │            │ Guided Data  │
│  Management  │            │   Designer   │            │   Importer   │
│  - Connect   │            │  - Vertex    │            │  - Sources   │
│  - Switch    │            │  - Edge      │            │  - Mapping   │
│  - Card View │            │  - Index     │            │  - Monitor   │
└──────────────┘            └──────────────┘            └──────────────┘
      │                                                       │
      └───────────────────────────┬───────────────────────────┘
                                  │
                                  ▼
                    ┌──────────────────────────┐
                    │Graph Analysis & Explorer │
                    │ - Gremlin Console        │
                    │ - Algorithm Execution    │
                    │ - Topology Exploration   │
                    └──────────────────────────┘
```
</details>

## Features

- Graph connection management, supporting to easily switch graph to operate
- Graph data load, supporting to load large amounts of data from files into hugegraph-server
- Schema management, supporting to easily perform schema manipulation and display
- Graph analysis and graphical display, supporting to build a query via the gremlin or algorithms with a little effort then will get cool graphical results

## Quick Start

There are three ways to get HugeGraph-Hubble:

- Download the Toolchain binary package
- Source code compilation
- Use Docker image (Convenient for Test/Dev)

And you can find more details in the [doc](https://hugegraph.apache.org/docs/quickstart/hugegraph-hubble/#2-deploy)

### 1. Download the Toolchain binary package

`hubble` is in the `toolchain` project. First, download the binary tar tarball

```bash
wget https://downloads.apache.org/hugegraph/{version}/apache-hugegraph-toolchain-{version}.tar.gz
tar -xvf apache-hugegraph-toolchain-{version}.tar.gz
cd apache-hugegraph-toolchain-{version}/apache-hugegraph-hubble-{version}
```

Run `hubble`:

```
bin/start-hubble.sh
```

Then use a web browser to access `ip:8088` and you can see the `Hubble` page. You can stop the service using bin/stop-hubble.sh.

### 2. Clone source code then compile and install

> Note: Compiling Hubble requires the user's local environment to have Node.js V18.20.8 and yarn installed.

```bash
apt install curl build-essential
curl -o- https://raw.githubusercontent.com/nvm-sh/nvm/v0.39.1/install.sh | bash
source ~/.bashrc
nvm install 18.20.8
```

Then, verify that the installed Node.js version is 18.20.8.

```bash
node -v
```

install `yarn` by the command below:

```bash
npm install -g yarn
```

Download the toolchain source code.

```bash
git clone https://github.com/apache/hugegraph-toolchain.git
```

Compile `hubble`. It depends on the loader and client, so you need to build these dependencies in advance during the compilation process (you can skip this step later).

```bash
cd hugegraph-toolchain
sudo pip install -r hugegraph-hubble/hubble-dist/assembly/travis/requirements.txt
mvn install -pl hugegraph-client,hugegraph-loader -am -Dmaven.javadoc.skip=true -DskipTests -ntp
cd hugegraph-hubble
mvn -e compile package -Dmaven.javadoc.skip=true -Dmaven.test.skip=true -ntp
cd apache-hugegraph-hubble-*
```

Run `hubble`

```bash
bin/start-hubble.sh -d
```

### 3. User docker image (Convenient for Test/Dev)

We can use `docker run -itd --name=hubble -p 8088:8088 hugegraph/hubble` to quickly start [hubble](https://hub.docker.com/r/hugegraph/hubble). An you can visit [hubble deploy doc](https://hugegraph.apache.org/docs/quickstart/hugegraph-hubble/#2-deploy) for more details.

Then we should follow the [hubble workflow doc](https://hugegraph.apache.org/docs/quickstart/hugegraph-hubble/#3platform-workflow) to create the graph.

> Note: 
> 1. The docker image of hugegraph-hubble is a convenience release, but not **official distribution** artifacts. You can find more details from [ASF Release Distribution Policy](https://infra.apache.org/release-distribution.html#dockerhub).
> 
> 2. Recommand to use `release tag`(like `1.0.0`) for the stable version. Use `latest` tag to experience the newest functions in development.

## Doc

[The hubble homepage](https://hugegraph.apache.org/docs/quickstart/hugegraph-hubble/) contains more information about it.

## License

hugegraph-hubble is licensed under Apache 2.0 License.

## Notice

The `hubble-fe` folder contains the frontend code, including all related source code for the frontend.

The `hubble-be` folder contains the backend code, including all related source code for the backend.

The `hubble-dist` folder contains files that can be directly used for deployment, generated after compiling and packaging both the frontend and backend code.

## Java 17 and metadata storage

Hubble 1.8 requires Java 17 and uses Spring Boot 3 with H2 2.x only.
MySQL remains available as a Loader source, but is no longer a Hubble metadata database.
The default metadata URL is `jdbc:h2:file:./data/hubble-v2;DB_CLOSE_ON_EXIT=FALSE`.
Schema initialization is idempotent, and restarting with this same database retains metadata.

Start with a new H2 database when upgrading. Existing Hubble databases are not
migrated, deleted or rewritten automatically. Keep the old database and its matching
Hubble release together if you need to access old metadata; do not point the new
release at the old database. For database-open errors, first verify the database
version, credentials and file access; do not delete the old files. Before SQL initialization,
Hubble opens an existing local database read-only and checks the release schema
marker. Databases without that marker or with another schema version are refused
without initializing or modifying them. The marker is written only after the
complete new schema initializes successfully.

### Manual handling of H2 error 90097

H2 error `90097` means an operation required a write while the database was open
read-only. Hubble refuses startup when its read-only metadata check encounters
this error. An interrupted first initialization is one possible cause, but the
error alone does not prove that the database is empty or safe to remove.

1. Stop processes using this metadata database before investigating. Preserve
   the original database files and make a complete backup.
2. Check the Hubble/H2 versions, configured credentials, file permissions and
   whether the previous initialization completed. Do not put credentials or
   database contents into support logs.
3. If recovery is needed, have the operator evaluate it on an isolated backup
   copy with the matching H2 version. Opening a copy for writing may change it;
   Hubble does not switch the original database to write mode or recover it
   automatically. Keep the original files until recovery has been verified.
4. If the operator chooses to initialize fresh metadata instead, configure a
   new database path and retain the original database and its matching release.
   Existing metadata is not imported into the new database automatically.

Metadata connections accept local H2 file and named memory URLs. Use a name such
as `jdbc:h2:mem:metadata` for disposable tests; `jdbc:h2:mem` and `jdbc:h2:mem:`
are rejected before opening a connection. With the default `DB_CLOSE_DELAY=0`,
named memory metadata disappears when the last connection closes. Use a file
URL to retain metadata across restarts. The file prefix can
be omitted, for example `jdbc:h2:./data/hubble-v2`. Encrypted files using
`CIPHER=AES` retain their cipher setting during the read-only compatibility
check; configure the H2 file and user passwords through the password property.
Other URL settings are excluded from that probe so they cannot run SQL or change
the database before validation. Hikari
pool settings are applied before validating the final connection. Use the JDBC
URL, username and password directly; alternative DataSource/JNDI factories and
DataSource properties are unsupported. URLs containing `INIT` are refused so
connection creation cannot execute SQL before the compatibility check.

The packaged startup script opens `java.base/java.net` to the embedded Loader's
Hive ORC reader, whose URI interning still uses reflection. Custom Java launch
commands must include `--add-opens=java.base/java.net=ALL-UNNAMED` as shown above.
This exception is limited to Hubble/Loader processes; no global JVM setting is needed.

Packaging excludes metadata databases and other runtime files from the archive
without deleting them from a previously run release directory. Docker images
are populated from that archive in a fresh build-stage directory, rather than
copying a release directory that may contain local runtime data. The distributable
archive is under `target/`; packaging no longer creates a redundant release copy
under `hubble-dist/`.

The Hubble CI runs this module on Java 17 and checks its released Server 1.7
baseline on a separate Java 11 JVM. SDK dependencies remain the published 1.7
artifacts; building the server package does not install candidate SDK artifacts.
The shared build and Client compatibility changes are required before this module.

## Java 17 UI validation

The Java 17 Hubble UI was exercised against released Server 1.7 with authentication:
create a graph and sample schema, import CSV data, switch graphs, and query the
selected graph. Query execution waits until the graph context matches the current
route, including keyboard shortcuts during a graph switch.

![Server 1.7 query results after graph switching](docs/images/java17-validation/legacy17-query-after.jpg)

The standalone 1.5 path remains limited to core graph/schema/data operations;
it does not support GraphSpace management or PD mode. The metadata upgrade
procedure is described in [Java 17 and metadata storage](#java-17-and-metadata-storage).
