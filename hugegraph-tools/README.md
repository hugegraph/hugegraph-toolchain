# HugeGraph-Tools

HugeGraph-Tools is a customizable command line utility for deploying, managing and backing up/restoring graphs from HugeGraph database.

## Main Functions

- Deploy and clear HugeGraph-Server and HugeGraph-Studio automatically.
- Manage graphs and query with Gremlin from multiple HugeGraph databases easily.
- Backup/restore graph schema and graph data from/to HugeGraph databases conveniently, also support backup periodically

### Server-managed physical snapshots

The `snapshot-backup` and `snapshot-restore` commands submit tasks to HugeGraph
Server. The first release targets a single Server running an embedded RocksDB
graph under Kubernetes/Helm. The Server owns checkpoint capture, RocksDB
BackupEngine storage, graph-version metadata, retention, validation, and the
maintenance restore flow.

```bash
# Create a backup version; the first call creates the baseline automatically
bin/hugegraph --graph hugegraph snapshot-backup --repository daily --keep-num 3

# Supply the same request id when retrying after a lost response
bin/hugegraph --graph hugegraph snapshot-backup \
  --repository daily --keep-num 3 --request-id <request-id>

# Restore a version through the Server maintenance workflow
bin/hugegraph --graph hugegraph snapshot-restore \
  --repository daily --backup-id <backup-id> --confirm

# Discover versions after the original task output is no longer available
bin/hugegraph --graph hugegraph snapshot-list --repository daily
bin/hugegraph --graph hugegraph snapshot-get \
  --repository daily --backup-id <backup-id>
```

These commands do not accept a backup mode or a Server data-directory path.
Repository names are resolved by the Server configuration, and the CLI only
submits the task and reports its result. Pass the same repository to
`snapshot-list` and `snapshot-get` that was used for `snapshot-backup`; omitting
it could select the Server's default repository instead. The global
`--timeout` controls HTTP connection timeout; `snapshot-backup` and
`snapshot-restore` use `--task-timeout` for the task wait budget. If the wait
budget expires, keep the printed task id and query it with `task-get` while the
Server task continues.

## Learn More

The [tools homepage](https://hugegraph.apache.org/docs/quickstart/hugegraph-tools/) contains more information about it. 

## License

HugeGraph-Tools is licensed under Apache 2.0 License.
