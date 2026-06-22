#!/bin/bash
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
set -euo pipefail

if [[ $# -lt 1 || $# -gt 3 ]]; then
    echo "Usage: $0 <apache-hugegraph-hubble-*.tar.gz> [--json-output <path>]" >&2
    exit 1
fi

tarball=$1
json_output=""
if [[ $# -eq 3 ]]; then
    if [[ "$2" != "--json-output" ]]; then
        echo "Unknown option: $2" >&2
        exit 1
    fi
    json_output=$3
fi

if [[ ! -f "${tarball}" ]]; then
    echo "Hubble tarball not found: ${tarball}" >&2
    exit 1
fi

tmp_list=$(mktemp)
tmp_dir=$(mktemp -d)
native_list=$(mktemp)
trap 'rm -f "${tmp_list}" "${native_list}"; rm -rf "${tmp_dir}"' EXIT

tar -tzf "${tarball}" > "${tmp_list}"
root=$(sed -n '1s#/.*##p' "${tmp_list}")

if [[ -z "${root}" ]]; then
    echo "Unable to resolve tarball root directory" >&2
    exit 1
fi

tar -xzf "${tarball}" -C "${tmp_dir}"

required_paths=(
    "${root}/bin/"
    "${root}/conf/"
    "${root}/lib/"
    "${root}/ui/"
    "${root}/README.md"
    "${root}/LICENSE"
    "${root}/NOTICE"
    "${root}/licenses/"
)

for path in "${required_paths[@]}"; do
    if ! grep -qxF "${path}" "${tmp_list}"; then
        echo "Missing required distribution path: ${path}" >&2
        exit 1
    fi
done

for legal_file in LICENSE NOTICE README.md; do
    if [[ -z "$(tr -d '[:space:]' < "${tmp_dir}/${root}/${legal_file}")" ]]; then
        echo "Packaged ${legal_file} must not be empty" >&2
        exit 1
    fi
done

if ! grep -qE "^${root}/licenses/fe-licenses/" "${tmp_list}"; then
    echo "Missing frontend license directory: ${root}/licenses/fe-licenses/" >&2
    exit 1
fi

for residue in logs upload-files; do
    if grep -qE "^${root}/${residue}(/|$)" "${tmp_list}"; then
        echo "Runtime residue must not be packaged: ${residue}" >&2
        exit 1
    fi
done

blocked_patterns=(
    "node_modules"
    "\\.pid$"
    "\\.lock$"
    "\\.db$"
    "\\.log$"
)

for pattern in "${blocked_patterns[@]}"; do
    if grep -qE "(^|/)${pattern}(/|$)" "${tmp_list}"; then
        echo "Blocked runtime/development artifact found: ${pattern}" >&2
        grep -E "(^|/)${pattern}(/|$)" "${tmp_list}" >&2
        exit 1
    fi
done

if grep -qE "^${root}/ui/.*\\.map$" "${tmp_list}"; then
    echo "Frontend source maps must not be packaged" >&2
    grep -E "^${root}/ui/.*\\.map$" "${tmp_list}" >&2
    exit 1
fi

unknown_binary_pattern="^${root}/.*\\.(so|dylib|dll|exe|bin|tar|tgz|gz|zip)$"
if grep -qE "${unknown_binary_pattern}" "${tmp_list}"; then
    echo "Unknown outer binary/archive files found in distribution" >&2
    grep -E "${unknown_binary_pattern}" "${tmp_list}" >&2
    exit 1
fi

jar_count=$(grep -cE "^${root}/lib/[^/]+\\.jar$" "${tmp_list}" || true)
if [[ "${jar_count}" -eq 0 ]]; then
    echo "No dependency jars found under ${root}/lib" >&2
    exit 1
fi

license_count=$(grep -cE "^${root}/licenses/[^/]+$" "${tmp_list}" || true)
fe_license_count=$(grep -cE "^${root}/licenses/fe-licenses/[^/]+$" \
                   "${tmp_list}" || true)

while IFS= read -r jar_path; do
    local_jar="${tmp_dir}/${jar_path}"
    if jar tf "${local_jar}" | grep -qE "\\.(so|dylib|dll|jnilib)$"; then
        echo "${jar_path}" >> "${native_list}"
    fi
done < <(grep -E "^${root}/lib/[^/]+\\.jar$" "${tmp_list}")

native_jar_count=$(wc -l < "${native_list}" | tr -d ' ')
checksum_status="not_checked"
signature_status="not_checked"

if [[ -f "${tarball}.sha512" ]]; then
    expected_sha512=$(awk '{print $1}' "${tarball}.sha512")
    actual_sha512=$(shasum -a 512 "${tarball}" | awk '{print $1}')
    if [[ "${expected_sha512}" != "${actual_sha512}" ]]; then
        echo "SHA-512 checksum mismatch for ${tarball}" >&2
        exit 1
    fi
    checksum_status="passed"
fi

if [[ -f "${tarball}.asc" ]]; then
    gpg --verify "${tarball}.asc" "${tarball}" >/dev/null 2>&1
    signature_status="passed"
fi

if [[ -n "${json_output}" ]]; then
    mkdir -p "$(dirname "${json_output}")"
    {
        echo "{"
        echo "  \"tarball\": \"${tarball}\","
        echo "  \"root\": \"${root}\","
        echo "  \"jar_count\": ${jar_count},"
        echo "  \"license_count\": ${license_count},"
        echo "  \"fe_license_count\": ${fe_license_count},"
        echo "  \"checksum_status\": \"${checksum_status}\","
        echo "  \"signature_status\": \"${signature_status}\","
        echo "  \"native_jar_count\": ${native_jar_count},"
        echo "  \"native_jars\": ["
        sed 's#^#    "#; s#$#"#; $!s#$#,#' "${native_list}"
        echo "  ]"
        echo "}"
    } > "${json_output}"
fi

echo "Hubble distribution check passed: ${tarball}"
echo "JARs: ${jar_count}, license files: ${license_count}, FE license files: ${fe_license_count}, native-bearing JARs: ${native_jar_count}"
