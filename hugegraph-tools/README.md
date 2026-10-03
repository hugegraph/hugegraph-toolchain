# HugeGraph-Tools

HugeGraph-Tools is a customizable command line utility for deploying, managing and backing up/restoring graphs from HugeGraph database.

## Requirements

HugeGraph Tools 1.8 requires Java 17 or later. Set `JAVA_HOME` to the JDK used
for Tools; the server JVM can be configured independently. Upgrading Tools does
not convert existing backup files. Keep the original backup until its schema
and data have been restored and verified in a separate graph.

## Main Functions

- Deploy and clear HugeGraph-Server and HugeGraph-Studio automatically.
- Manage graphs and query with Gremlin from multiple HugeGraph databases easily.
- Backup/restore graph schema and graph data from/to HugeGraph databases conveniently, also support backup periodically

## Learn More

The [tools homepage](https://hugegraph.apache.org/docs/quickstart/hugegraph-tools/) contains more information about it. 

## License

HugeGraph-Tools is licensed under Apache 2.0 License.

## Java 17 validation

Run the functional suite against an isolated test server with the authentication
configuration from `assembly/travis`:

```bash
mvn test -Dtest=FuncTestSuite -pl hugegraph-tools -ntp
```

The suite includes compressed and plain JSON schema/data backup round trips,
including Unicode properties and edges. These tests clear the `hugegraph` test
graph before and after each round trip. Use a disposable server, never a graph
containing user data. To test a separate server, pass
`-Dtools.test.url=http://127.0.0.1:PORT`.

The Tools CI builds the locked Java 17 candidate server and its client-side
artifacts in an isolated Maven repository before compiling Tools. A passing
candidate run does not replace the separate regression run against a released
Server 1.7 or the validation of backups produced by an older Tools release.

To repeat the backup round trips on a running HDFS cluster, use:

```bash
mvn test -Dtest=BackupRestoreTest -pl hugegraph-tools -ntp \
  -Dtools.test.hdfs=hdfs://localhost:8020
```

Each run creates and removes its own `/tools-backup-*` HDFS directory. Plain
JSON backups on HDFS are read as plain streams, just like local backups; files
ending in `.zip` remain validated as ZIP archives.
