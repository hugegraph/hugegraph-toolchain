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
const moduleBuiltin = require('module');
const path = require('path');
const {authenticateUi} = require('./ui_auth');

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
  const hubbleRoot = path.resolve(__dirname, '../../..');
  const candidateModules = [
    process.env.PLAYWRIGHT_NODE_MODULES || '',
    path.join(hubbleRoot, 'hubble-fe', 'node_modules')
  ].filter(Boolean);
  try {
    return require('playwright');
  } catch (error) {
    for (const nodeModules of candidateModules) {
      try {
        const requireFrom = moduleBuiltin.createRequire(
          path.join(nodeModules, 'playwright', 'package.json')
        );
        return requireFrom('playwright');
      } catch (_) {
        // Continue to the next configured module root.
      }
    }
    throw new Error(
      'Playwright is required for UI browser smoke. Install/enable it before ' +
      'closing the browser gate, or set PLAYWRIGHT_NODE_MODULES. Original ' +
      'error: ' + error.message
    );
  }
}

async function main() {
  const hubbleUrl = (argValue('--hubble-url', process.env.HUBBLE_URL) ||
                     'http://127.0.0.1:8088').replace(/\/$/, '');
  const outputDir = path.resolve(argValue('--output-dir',
                                          '.workflow/hubble-v2-issue-694/evidence/ui'));
  const connId = argValue('--conn-id', process.env.HUBBLE_CONN_ID || '1');
  const username = argValue('--username', process.env.HUBBLE_USERNAME || 'admin');
  const password = argValue('--password', process.env.HUBBLE_PASSWORD || 'pa');
  const jsonOutput = argValue('--json-output', '');
  const { chromium } = await loadPlaywright();
  const executablePath = chromiumExecutablePath();

  fs.mkdirSync(outputDir, { recursive: true });
  const browser = await chromium.launch({ headless: true, executablePath });
  const context = await browser.newContext();
  const page = await context.newPage({ viewport: { width: 1440, height: 900 } });
  const network = [];
  const consoleErrors = [];

  await page.addInitScript(() => {
    window.localStorage.setItem('languageType', 'zh-CN');
  });
  const auth = await authenticateUi(context, page, hubbleUrl, username, password);

  page.on('response', async (response) => {
    const request = response.request();
    const url = response.url();
    if (url.includes('/api/v1.3/')) {
      let businessStatus = null;
      try {
        const body = await response.json();
        businessStatus = body && body.status !== undefined ? body.status : null;
      } catch (_) {
        // Non-JSON API responses are still represented by HTTP status.
      }
      network.push({
        method: request.method(),
        url,
        httpStatus: response.status(),
        ok: response.ok(),
        businessStatus
      });
    }
  });
  page.on('console', (message) => {
    if (message.type() === 'error') {
      consoleErrors.push(message.text());
    }
  });

  const routes = [
    { name: 'graphspace', path: '/graphspace',
      requiredApis: ['/api/v1.3/graphspaces'],
      textPattern: /图空间|Graph Space/ },
    { name: 'gremlin', path: '/gremlin',
      requiredApis: ['/api/v1.3/graphspaces/list'],
      textPattern: /Gremlin|图查询|查询/ },
    { name: 'algorithms', path: '/algorithms',
      requiredApis: ['/api/v1.3/graphspaces/list'],
      textPattern: /算法|Algorithm|OLTP|OLAP/ },
    { name: 'asyncTasks', path: '/asyncTasks',
      requiredApis: ['/api/v1.3/graphspaces/list'],
      textPattern: /异步|Async|Task|任务/ }
  ];

  const results = [];
  try {
    for (const route of routes) {
      network.length = 0;
      await page.goto(hubbleUrl + route.path, {
        waitUntil: 'networkidle',
        timeout: 30000
      });
      await page.waitForTimeout(500);
      const screenshot = path.join(outputDir, `${route.name}.png`);
      await page.screenshot({ path: screenshot, fullPage: true });
      const text = await page.locator('body').innerText({ timeout: 5000 });
      const rawKeyPattern = new RegExp(
        '\\b(addition|analysis|async-tasks|common|home|manage|navigation|' +
        'server-data-import|Topbar)\\.[A-Za-z0-9_.-]+'
      );
      const matchedApis = route.requiredApis.map((requiredApi) => {
        const entries = network.filter((entry) => entry.url.includes(requiredApi));
        return {
          requiredApi,
          entries,
          passed: entries.some((entry) => entry.ok &&
                                  (entry.businessStatus === null ||
                                   entry.businessStatus === 200))
        };
      });
      results.push({
        route: route.path,
        screenshot,
        matchedApis,
        rawI18nKeyFound: rawKeyPattern.test(text),
        routeTextMatched: route.textPattern.test(text),
        notFoundPage: /404|页面不存在|Not Found/.test(text),
        requestCount: network.length
      });
    }
  } finally {
    await browser.close();
  }

  const report = {
    hubbleUrl,
    authenticatedUser: auth.user.user_name,
    authLevel: auth.level,
    results,
    consoleErrors,
    status: results.every((result) => (
      result.matchedApis.every((api) => api.passed) &&
      result.routeTextMatched &&
      !result.rawI18nKeyFound &&
      !result.notFoundPage
    )) && consoleErrors.length === 0 ? 'passed' : 'failed'
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
