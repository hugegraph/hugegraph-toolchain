#!/usr/bin/env node
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const fs = require('fs');
const path = require('path');

const hubbleRoot = path.resolve(__dirname, '../../..');
const resourcesRoot = path.join(
  hubbleRoot,
  'hubble-fe',
  'src',
  'i18n',
  'resources'
);
const zhRoot = path.join(resourcesRoot, 'zh-CN');
const enRoot = path.join(resourcesRoot, 'en-US');

function listJsonFiles(root) {
  const result = [];
  const pending = [root];

  while (pending.length > 0) {
    const current = pending.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        pending.push(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.json')) {
        result.push(path.relative(root, fullPath));
      }
    }
  }

  return result.sort();
}

function flatten(value, prefix = '', output = {}) {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    for (const [key, child] of Object.entries(value)) {
      const nextPrefix = prefix ? `${prefix}.${key}` : key;
      flatten(child, nextPrefix, output);
    }
    return output;
  }

  output[prefix] = value;
  return output;
}

function readJson(root, relativePath) {
  const fullPath = path.join(root, relativePath);
  return JSON.parse(fs.readFileSync(fullPath, 'utf8'));
}

function diffSet(left, right) {
  return [...left].filter((item) => !right.has(item));
}

const zhFiles = listJsonFiles(zhRoot);
const enFiles = listJsonFiles(enRoot);
const zhFileSet = new Set(zhFiles);
const enFileSet = new Set(enFiles);
let failures = [];

for (const file of diffSet(zhFileSet, enFileSet)) {
  failures.push(`Missing en-US i18n file: ${file}`);
}
for (const file of diffSet(enFileSet, zhFileSet)) {
  failures.push(`Missing zh-CN i18n file: ${file}`);
}

for (const file of zhFiles.filter((item) => enFileSet.has(item))) {
  const zhEntries = flatten(readJson(zhRoot, file));
  const enEntries = flatten(readJson(enRoot, file));
  const zhKeys = new Set(Object.keys(zhEntries));
  const enKeys = new Set(Object.keys(enEntries));

  for (const key of diffSet(zhKeys, enKeys)) {
    failures.push(`Missing en-US key: ${file}:${key}`);
  }
  for (const key of diffSet(enKeys, zhKeys)) {
    failures.push(`Missing zh-CN key: ${file}:${key}`);
  }

  for (const [locale, entries] of [
    ['zh-CN', zhEntries],
    ['en-US', enEntries]
  ]) {
    for (const [key, value] of Object.entries(entries)) {
      if (typeof value !== 'string') {
        failures.push(`Non-string ${locale} value: ${file}:${key}`);
      } else if (value.trim() === '') {
        failures.push(`Empty ${locale} value: ${file}:${key}`);
      }
    }
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log('Hubble i18n check passed');
