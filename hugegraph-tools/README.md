# HugeGraph-Tools

HugeGraph-Tools is a customizable command line utility for deploying, managing and backing up/restoring graphs from HugeGraph database.

## Main Functions

- Deploy and clear HugeGraph-Server and HugeGraph-Studio automatically.
- Manage graphs and query with Gremlin from multiple HugeGraph databases easily.
- Backup/restore graph schema and graph data from/to HugeGraph databases conveniently, also support backup periodically

## Backup and migration failures

If a vertex or edge data page still fails after retries, backup fails and `migrate` does not start restoring the target.
Partial backup files and shard diagnostics are retained for inspection; use a new backup directory when retrying.
The backup directory and `--log` directory are independent; the latter defaults to `./logs`.
Initialization clears files ending in `_shards` from the selected log directory. Before retrying, copy those diagnostic
files elsewhere or select a new `--log` directory as well as a new backup directory.
Use `migrate --keep-local-data` to retain the local backup after a successful restore, and verify the target data before
switching applications to it.

## Learn More

The [tools homepage](https://hugegraph.apache.org/docs/quickstart/hugegraph-tools/) contains more information about it. 

## Testing

The `BackupRestoreTest` functional tests clear the `hugegraph` graph. Run them only against a disposable server instance, explicitly selected with `-Dtools.test.disposable=true`. This applies even when `tools.test.url` is set. Without that opt-in, these tests skip before creating a client. Their default backup path uses Hadoop's `file:///` filesystem; `tools.test.hdfs` can select a separate filesystem fixture.

## License

HugeGraph-Tools is licensed under Apache 2.0 License.
