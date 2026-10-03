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

function get_params() {
  ENGINE_ARGS=()
  HUGEGRAPH_ARGS=()
  local file="" mode=""
  while (("$#")); do
    case "$1" in
      -g | --graph | -s | --schema | -h | -i | --host | -p | --port | --username | --password | --token | --protocol | \
      --pd-peers | --pd-token | --meta-endpoints | --route-type | --cluster | --graphspace | \
      --trust-store-file | --trust-store-password | --clear-all-data | --clear-timeout | \
      --incremental-mode | --failure-mode | --batch-insert-threads | --single-insert-threads | \
      --max-conn | --max-conn-per-route | --batch-size | --max-parse-errors | --max-insert-errors | \
      --timeout | --shutdown-timeout | --retry-times | --retry-interval | --check-vertex | \
      --print-progress | --dry-run | --sink-type | --vertex-partitions | --edge-partitions | \
      --vertex-table-name | --edge-table-name | --hbase-zk-quorum | --hbase-zk-port | --hbase-zk-parent)
        if (( $# < 2 )); then
          echo "Missing value for $1" >&2
          return 2
        fi
        HUGEGRAPH_ARGS+=("$1" "$2")
        shift 2
        ;;
      -help | --help)
        HUGEGRAPH_ARGS+=("$1")
        shift
        ;;
      -f | --file | --deploy-mode)
        if (( $# < 2 )); then
          echo "Missing value for $1" >&2
          return 2
        fi
        if [ "$1" = '--file' ] || [ "$1" = '-f' ]; then
          file=$2
        else
          mode=$2
          ENGINE_ARGS+=("$1" "$2")
        fi
        shift 2
        ;;
      *)
        ENGINE_ARGS+=("$1")
        shift
        ;;
    esac
  done

  if [ -n "$file" ]; then
    if [ "$mode" = 'cluster' ]; then
      HUGEGRAPH_ARGS+=(--file "${file##*/}")
      ENGINE_ARGS+=(--files "$file")
    else
      HUGEGRAPH_ARGS+=(--file "$file")
    fi
  fi

  # Retain the existing string interface for other engine launchers.
  ENGINE_PARAMS="${ENGINE_ARGS[*]}"
  HUGEGRAPH_PARAMS="${HUGEGRAPH_ARGS[*]}"
}
