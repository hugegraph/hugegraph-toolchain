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
      'Playwright is required for runtime i18n smoke. Original error: ' +
      error.message
    );
  }
}

async function captureLanguage(page, hubbleUrl, language, screenshot) {
  await page.addInitScript((value) => {
    window.localStorage.setItem('languageType', value);
  }, language);
  await page.goto(hubbleUrl + '/graph-management', {
    waitUntil: 'networkidle',
    timeout: 30000
  });
  await page.screenshot({ path: screenshot, fullPage: true });
  return await page.locator('body').innerText({ timeout: 5000 });
}

async function main() {
  const hubbleUrl = (argValue('--hubble-url', process.env.HUBBLE_URL) ||
                     'http://127.0.0.1:8088').replace(/\/$/, '');
  const outputDir = path.resolve(argValue('--output-dir',
                                          '.workflow/hubble-v2-issue-694/evidence/ui'));
  const jsonOutput = argValue('--json-output', '');
  const { chromium } = await loadPlaywright();
  const executablePath = chromiumExecutablePath();
  fs.mkdirSync(outputDir, { recursive: true });

  const browser = await chromium.launch({ headless: true, executablePath });
  const page = await browser.newPage({ viewport: { width: 1440, height: 900 } });
  let zhText;
  let enText;
  try {
    zhText = await captureLanguage(page, hubbleUrl, 'zh-CN',
                                   path.join(outputDir, 'i18n-zh-CN.png'));
    enText = await captureLanguage(page, hubbleUrl, 'en-US',
                                   path.join(outputDir, 'i18n-en-US.png'));
  } finally {
    await browser.close();
  }

  const report = {
    hubbleUrl,
    zhContainsChinese: /[\u4e00-\u9fff]/.test(zhText),
    enContainsGraphManager: /Graph|Management|graph|management/.test(enText),
    textChanged: zhText !== enText,
    status: 'failed'
  };
  report.status = report.zhContainsChinese && report.enContainsGraphManager &&
                  report.textChanged ? 'passed' : 'failed';

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
