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
BIN_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR=$(dirname "${BIN_DIR}")
LIB_DIR=${APP_DIR}/lib

source "$BIN_DIR"/get-params.sh
get_params "$@" || exit $?

shopt -s nullglob
ASSEMBLY_JARS=("${LIB_DIR}"/apache-hugegraph-loader-*-shaded.jar)
if [ "${#ASSEMBLY_JARS[@]}" -ne 1 ]; then
  echo "Expected exactly one shaded HugeGraph Loader jar in ${LIB_DIR}" >&2
  exit 1
fi
ASSEMBLY_JAR=${ASSEMBLY_JARS[0]}
# The shaded application already contains its runtime dependencies.
CMD=("${SPARK_HOME}/bin/spark-submit" --class org.apache.hugegraph.loader.spark.HugeGraphSparkLoader)
CMD+=("${ENGINE_ARGS[@]}" "$ASSEMBLY_JAR" "${HUGEGRAPH_ARGS[@]}")
exec "${CMD[@]}"
