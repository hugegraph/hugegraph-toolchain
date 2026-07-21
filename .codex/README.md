# Toolchain local environment

Codex reads `.codex/environments/environment.toml` when it creates a worktree.
The setup is intentionally lightweight: it pins Node 18.20.8 and installs the
frontend dependencies without building or starting services.

## Runtime model

- Each worktree gets a stable frontend port in `3001-3099`.
- Up to three frontend dev servers are recommended; additional servers warn.
- One Hubble backend listens on `8088`. The first worktree owns it until the
  `Hubble BE Switch` action explicitly replaces it.
- HStore uses the sibling server repository's Compose file plus the tracked
  low-memory override. Images use the local `latest` tag and never pull during
  startup. `Infrastructure Pull` is the explicit update operation.
- Low-memory Server tuning is best-effort across `latest` image layouts: known
  properties are applied even when absent from the template, while unavailable
  YAML settings produce a warning and keep the image default. It is a local
  functional-test profile, not a memory or compatibility guarantee.
- HStore starts with Server access control enabled. Connect Hubble to
  `http://127.0.0.1:8080` as `admin` with password `pa`, or set
  `HUBBLE_SERVER_PASSWORD` before the first start to use a local alternative.
  The password only initializes a new local data volume; stopping services
  preserves the original password. Use `Infrastructure Reset` to initialize
  fresh local data if that password is lost. This known credential is only for
  local testing; never reuse it elsewhere.
- `Infrastructure Stop` preserves HStore data. `Infrastructure Reset` requires
  typing `RESET` before it deletes the Compose volumes.
- Runtime ownership files live under `~/.codex/run/`, not in a worktree.

Set `HUGEGRAPH_SERVER_REPO` when the server repository is not next to
toolchain. Set `HUBBLE_LOADER_HOME` when no packaged Loader exists under the
source checkout. Set `HUBBLE_MVND_BIN` to override the Java 11-compatible mvnd
binary. Secrets must stay in local environment variables or an untracked local
file; do not add them to the environment TOML.

The low-memory HStore profile is intended only for local, small-data functional
testing. Use the balanced profile for larger Loader samples. Neither Docker
Desktop nor these constrained profiles are valid performance baselines.
