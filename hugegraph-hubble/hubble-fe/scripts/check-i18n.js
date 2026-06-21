#!/usr/bin/env node

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

const fs = require('fs');
const path = require('path');

const projectRoot = path.resolve(__dirname, '..');
const resourcesRoot = path.join(projectRoot, 'src', 'i18n', 'resources');
const sourceRoot = path.join(projectRoot, 'src');
const locales = ['zh-CN', 'en-US'];
const ignoredSourceDirs = new Set(['i18n', 'node_modules']);
const problems = [];

const localeData = Object.fromEntries(
  locales.map((locale) => [locale, loadLocale(locale)])
);

checkSymmetricKeys();
checkLocaleValues();
checkStaticTranslationUsage();

if (problems.length > 0) {
  console.error(`i18n check failed with ${problems.length} issue(s):`);
  for (const problem of problems) {
    console.error(`- ${problem}`);
  }
  process.exit(1);
}

console.log(JSON.stringify({
  status: 'passed',
  locales: Object.fromEntries(
    locales.map((locale) => [locale, Object.keys(localeData[locale].flat).length])
  ),
  checkedStaticKeys: collectStaticTranslationKeys().length
}, null, 2));

function loadLocale(locale) {
  const root = path.join(resourcesRoot, locale);
  const files = listFiles(root, (file) => file.endsWith('.json'));
  const byFile = new Map();
  const merged = {};

  for (const file of files) {
    const relativePath = path.relative(root, file);
    const json = JSON.parse(fs.readFileSync(file, 'utf8'));
    byFile.set(relativePath, flatten(json));
  }

  for (const moduleFile of getMergedModuleFiles(locale)) {
    const file = path.join(root, moduleFile);
    if (fs.existsSync(file)) {
      deepMerge(merged, JSON.parse(fs.readFileSync(file, 'utf8')));
    }
  }

  return {
    byFile,
    flat: flatten(Object.fromEntries(
      Array.from(byFile.entries()).map(([file, flat]) => [file, flat])
    )),
    merged,
    mergedFlat: flatten(merged)
  };
}

function getMergedModuleFiles(locale) {
  const indexPath = path.join(resourcesRoot, locale, 'index.js');
  const indexText = fs.readFileSync(indexPath, 'utf8');
  const imports = new Map();
  const importPattern = /import\s+(\w+)\s+from\s+'\.\/([^']+\.json)'/g;
  let match;

  for (const barrelMatch of getNamedImports(indexText)) {
    const names = barrelMatch[1].split(',').map((name) => name.trim()).filter(Boolean);
    const barrel = path.join(resourcesRoot, locale, barrelMatch[2], 'index.js');
    if (!fs.existsSync(barrel)) {
      continue;
    }
    const barrelText = fs.readFileSync(barrel, 'utf8');
    const barrelImports = new Map();
    importPattern.lastIndex = 0;
    while ((match = importPattern.exec(barrelText)) !== null) {
      barrelImports.set(match[1], path.join(barrelMatch[2], match[2]));
    }
    for (const name of names) {
      if (barrelImports.has(name)) {
        imports.set(name, barrelImports.get(name));
      }
    }
  }

  while ((match = importPattern.exec(indexText)) !== null) {
    imports.set(match[1], match[2]);
  }

  return getMergeArguments(indexText)
    .map((name) => imports.get(name))
    .filter(Boolean);
}

function getNamedImports(text) {
  const imports = [];
  const lines = text.split('\n');
  for (let index = 0; index < lines.length; index++) {
    const line = lines[index];
    const inlineMatch = line.match(/^import\s+\{([^}]+)\}\s+from\s+'\.\/([^']+)';?$/);
    if (inlineMatch) {
      imports.push(inlineMatch);
      continue;
    }
    if (!line.match(/^import\s+\{\s*$/)) {
      continue;
    }
    const names = [];
    index++;
    while (index < lines.length && !lines[index].includes('}')) {
      names.push(lines[index].trim());
      index++;
    }
    if (index >= lines.length) {
      break;
    }
    const sourceMatch = lines[index].match(/^\}\s+from\s+'\.\/([^']+)';?$/);
    if (sourceMatch) {
      imports.push([null, names.join('\n'), sourceMatch[1]]);
    }
  }
  return imports;
}

function getMergeArguments(indexText) {
  const start = indexText.indexOf('merge(');
  if (start === -1) {
    return [];
  }
  let depth = 0;
  let end = start;
  for (; end < indexText.length; end++) {
    if (indexText[end] === '(') {
      depth++;
    } else if (indexText[end] === ')') {
      depth--;
      if (depth === 0) {
        break;
      }
    }
  }
  return indexText
    .slice(start + 'merge('.length, end)
    .split(',')
    .map((name) => name.trim())
    .filter(Boolean);
}

function checkSymmetricKeys() {
  const zhFiles = localeData['zh-CN'].byFile;
  const enFiles = localeData['en-US'].byFile;
  const allFiles = new Set([...zhFiles.keys(), ...enFiles.keys()]);

  for (const file of allFiles) {
    if (!zhFiles.has(file)) {
      problems.push(`zh-CN missing resource file ${file}`);
      continue;
    }
    if (!enFiles.has(file)) {
      problems.push(`en-US missing resource file ${file}`);
      continue;
    }
    const zhKeys = new Set(Object.keys(zhFiles.get(file)));
    const enKeys = new Set(Object.keys(enFiles.get(file)));
    for (const key of zhKeys) {
      if (!enKeys.has(key)) {
        problems.push(`en-US missing key ${file}:${key}`);
      }
    }
    for (const key of enKeys) {
      if (!zhKeys.has(key)) {
        problems.push(`zh-CN missing key ${file}:${key}`);
      }
    }
  }
}

function checkLocaleValues() {
  for (const locale of locales) {
    for (const [file, entries] of localeData[locale].byFile.entries()) {
      for (const [key, value] of Object.entries(entries)) {
        if (typeof value !== 'string') {
          continue;
        }
        if (value.trim() === '') {
          problems.push(`${locale} empty value ${file}:${key}`);
        }
        if (
          locale === 'en-US' &&
          /[\u4e00-\u9fff]/.test(value)
        ) {
          problems.push(`${locale} Chinese text ${file}:${key}=${value}`);
        }
        if (/\b(TODO|TBD|xxx)\b/i.test(key) || /\b(TODO|TBD|xxx)\b/i.test(value)) {
          problems.push(`${locale} placeholder ${file}:${key}=${value}`);
        }
        if (looksLikeRawKey(value) && value === key) {
          problems.push(`${locale} raw key value ${file}:${key}`);
        }
      }
    }
  }
}

function checkStaticTranslationUsage() {
  const staticKeys = collectStaticTranslationKeys();
  const zhMerged = localeData['zh-CN'].mergedFlat;
  const enMerged = localeData['en-US'].mergedFlat;

  for (const key of staticKeys) {
    if (!Object.prototype.hasOwnProperty.call(zhMerged, key)) {
      problems.push(`zh-CN missing merged static t() key ${key}`);
    }
    if (!Object.prototype.hasOwnProperty.call(enMerged, key)) {
      problems.push(`en-US missing merged static t() key ${key}`);
    }
  }
}

function collectStaticTranslationKeys() {
  const keys = new Set();
  for (const file of listFiles(sourceRoot, isJavaScriptLikeFile)) {
    if (shouldIgnoreSource(file)) {
      continue;
    }
    const content = fs.readFileSync(file, 'utf8');
    for (const match of content.matchAll(/\bt\(\s*['"`]([^'"`]+)['"`]/g)) {
      if (match[1].includes('${')) {
        continue;
      }
      keys.add(match[1]);
    }
  }
  return Array.from(keys).sort();
}

function shouldIgnoreSource(file) {
  const relative = path.relative(sourceRoot, file);
  return relative.split(path.sep).some((segment) => ignoredSourceDirs.has(segment));
}

function isJavaScriptLikeFile(file) {
  return /\.(js|jsx|ts|tsx)$/.test(file);
}

function looksLikeRawKey(value) {
  return /^[A-Za-z][A-Za-z0-9_-]*(\.[A-Za-z0-9_-]+)+$/.test(value);
}

function listFiles(root, predicate) {
  const out = [];
  for (const entry of fs.readdirSync(root, {withFileTypes: true})) {
    const file = path.join(root, entry.name);
    if (entry.isDirectory()) {
      out.push(...listFiles(file, predicate));
    } else if (predicate(file)) {
      out.push(file);
    }
  }
  return out;
}

function flatten(obj, prefix = '', out = {}) {
  for (const [key, value] of Object.entries(obj)) {
    const nextKey = prefix ? `${prefix}.${key}` : key;
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      flatten(value, nextKey, out);
    } else {
      out[nextKey] = value;
    }
  }
  return out;
}

function deepMerge(target, source) {
  for (const [key, value] of Object.entries(source)) {
    if (value && typeof value === 'object' && !Array.isArray(value)) {
      if (!target[key] || typeof target[key] !== 'object' || Array.isArray(target[key])) {
        target[key] = {};
      }
      deepMerge(target[key], value);
    } else {
      target[key] = value;
    }
  }
  return target;
}
