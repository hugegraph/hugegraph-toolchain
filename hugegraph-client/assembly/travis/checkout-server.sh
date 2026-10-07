#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements. See the NOTICE file distributed with this
# work for additional information regarding copyright ownership. The ASF
# licenses this file to You under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations
# under the License.
#
set -euo pipefail

if [[ $# -ne 2 ]]; then
    echo "Usage: checkout-server.sh <commit SHA> <new checkout directory>" >&2
    exit 1
fi

COMMIT_ID=$1
CHECKOUT_DIR=$2
SERVER_REPOSITORY=${SERVER_REPOSITORY:-apache/hugegraph}
SERVER_FETCH_REF=${SERVER_FETCH_REF:-$COMMIT_ID}
if [[ ! "$COMMIT_ID" =~ ^([0-9a-fA-F]{6}|[0-9a-fA-F]{40})$ ]] ||
   [[ ! "$SERVER_REPOSITORY" =~ ^[a-zA-Z0-9][a-zA-Z0-9_.-]*/[a-zA-Z0-9][a-zA-Z0-9_.-]*$ ]]; then
    echo "Expected a six-character or full commit SHA and GitHub owner/repository" >&2
    exit 1
fi

if [[ ${#COMMIT_ID} -eq 6 ]]; then
    SHORT_COMMIT=$COMMIT_ID
    COMMIT_ID=$(python3 - "$SERVER_REPOSITORY" "$SHORT_COMMIT" <<'PY_RESOLVE'
import json
import os
import re
import sys
import urllib.error
import urllib.request

repository, short = sys.argv[1:]
headers = {'Accept': 'application/vnd.github+json', 'User-Agent': 'hugegraph-toolchain-ci'}
token = os.environ.get('GITHUB_TOKEN') or os.environ.get('GH_TOKEN')
if token:
    headers['Authorization'] = 'Bearer ' + token
request = urllib.request.Request(f'https://api.github.com/repos/{repository}/commits/{short}', headers=headers)
try:
    with urllib.request.urlopen(request, timeout=30) as response:
        sha = json.load(response)['sha']
    if not isinstance(sha, str) or not re.fullmatch(r'[0-9a-fA-F]{40}', sha) or not sha.lower().startswith(short.lower()):
        raise ValueError('Invalid commit identity')
except (OSError, ValueError, KeyError, TypeError):
    raise SystemExit('Unable to resolve the abbreviated Server commit') from None
print(sha.lower())
PY_RESOLVE
    )
fi
# Git transport requires a complete object ID, not an abbreviated commit prefix.
if [[ "$SERVER_FETCH_REF" =~ ^[0-9a-fA-F]{6}$ ]]; then
    FETCH_PREFIX=$(printf '%s' "$SERVER_FETCH_REF" | tr '[:upper:]' '[:lower:]')
    if [[ "$(printf '%s' "$COMMIT_ID" | tr '[:upper:]' '[:lower:]')" != "$FETCH_PREFIX"* ]]; then
        echo "Server checkout mismatch: selected commit differs from fetch prefix" >&2
        exit 1
    fi
    SERVER_FETCH_REF=$COMMIT_ID
fi

# Fetch a reachable ref, then verify it against the resolved SHA before building.
# A moving branch or PR must fail closed rather than silently changing the baseline.
[[ ! -e "$CHECKOUT_DIR" ]] || { echo "Checkout directory already exists" >&2; exit 1; }
git init "$CHECKOUT_DIR"
git -C "$CHECKOUT_DIR" remote add origin "https://github.com/${SERVER_REPOSITORY}.git"
git -C "$CHECKOUT_DIR" fetch --depth 1 -- origin "$SERVER_FETCH_REF"
git -C "$CHECKOUT_DIR" checkout --detach FETCH_HEAD
ACTUAL_COMMIT=$(git -C "$CHECKOUT_DIR" rev-parse HEAD)
if [[ "$ACTUAL_COMMIT" != "$(printf '%s' "$COMMIT_ID" | tr '[:upper:]' '[:lower:]')" ]]; then
    echo "Server checkout mismatch: expected ${COMMIT_ID:0:6}, got ${ACTUAL_COMMIT:0:6}" >&2
    exit 1
fi
printf 'Server source: %s@%s\n' "$SERVER_REPOSITORY" "${ACTUAL_COMMIT:0:6}"
