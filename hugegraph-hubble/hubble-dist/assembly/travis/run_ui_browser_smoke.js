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

'use strict';

const fs = require('fs');
const path = require('path');

const MAC_CHROME_PATHS = [
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/Applications/Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing',
  path.join(process.env.HOME || '',
            'Library/Caches/ms-playwright/chromium-1226/chrome-mac-arm64/' +
            'Google Chrome for Testing.app/Contents/MacOS/Google Chrome for Testing')
];

function argValue(name, fallback) {
  const index = process.argv.indexOf(name);
  if (index >= 0 && index + 1 < process.argv.length) {
    return process.argv[index + 1];
  }
  return fallback;
}

function chromiumExecutablePath() {
  const configured = argValue('--chromium-executable',
                              process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ||
                              process.env.CHROME_PATH || '');
  if (configured) {
    return configured;
  }
  for (const candidate of MAC_CHROME_PATHS) {
    if (candidate && fs.existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
}

async function loadPlaywright() {
  try {
    return require('playwright');
  } catch (error) {
    throw new Error(
      'Playwright is required for UI browser smoke. Install/enable it before ' +
      'closing the browser gate. Original error: ' + error.message
    );
  }
}

async function main() {
  const hubbleUrl = (argValue('--hubble-url', process.env.HUBBLE_URL) ||
                     'http://127.0.0.1:8088').replace(/\/$/, '');
  const outputDir = path.resolve(argValue('--output-dir',
                                          '.workflow/hubble-v2-issue-694/evidence/ui'));
  const connId = argValue('--conn-id', process.env.HUBBLE_CONN_ID || '1');
  const jsonOutput = argValue('--json-output', '');
  const { chromium } = await loadPlaywright();
  const executablePath = chromiumExecutablePath();

  fs.mkdirSync(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  const network = [];
  const consoleErrors = [];

  page.on('requestfinished', (request) => {
    const url = request.url();
    if (url.includes('/api/v1.2/')) {
      network.push({ method: request.method(), url });
    }
  });
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  const routes = [
    { name: 'graph-management', path: '/graph-management',
      requiredApi: '/api/v1.2/graph-connections' },
    { name: 'metadata-configs', path: `/graph-management/${connId}/metadata-configs`,
      requiredApi: `/api/v1.2/graph-connections/${connId}/schema/` },
    { name: 'data-import', path: `/graph-management/${connId}/data-import/import-manager`,
      requiredApi: `/api/v1.2/graph-connections/${connId}/job-manager` },
    { name: 'data-analyze', path: `/graph-management/${connId}/data-analyze`,
      requiredApi: `/api/v1.2/graph-connections/${connId}/schema/` },
    { name: 'async-tasks', path: `/graph-management/${connId}/async-tasks`,
      requiredApi: `/api/v1.2/graph-connections/${connId}/async-tasks` }
  ];

  const results = [];
  try {
    for (const route of routes) {
      network.length = 0;
      await page.goto(hubbleUrl + route.path, {
        waitUntil: 'networkidle',
        timeout: 30000
      });
      const screenshot = path.join(outputDir, `${route.name}.png`);
      await page.screenshot({ path: screenshot, fullPage: true });
      const text = await page.locator('body').innerText({ timeout: 5000 });
      const rawKeyPattern = /\b(addition|data-analyze|async-tasks|server-data-import)\.[A-Za-z0-9_.-]+/;
      const apiMatched = network.some((entry) => entry.url.includes(route.requiredApi));
      results.push({
        route: route.path,
        screenshot,
        apiMatched,
        requiredApi: route.requiredApi,
        rawI18nKeyFound: rawKeyPattern.test(text),
        requestCount: network.length
      });
    }
  } finally {
    await browser.close();
  }

  const report = {
    hubbleUrl,
    results,
    consoleErrors,
    status: results.every((result) => result.apiMatched &&
                                      !result.rawI18nKeyFound) ? 'passed' : 'failed'
  };
  if (jsonOutput) {
    fs.mkdirSync(path.dirname(path.resolve(jsonOutput)), { recursive: true });
    fs.writeFileSync(jsonOutput, JSON.stringify(report, null, 2) + '\n');
  }
  console.log(JSON.stringify(report, null, 2));
  if (report.status !== 'passed') {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
