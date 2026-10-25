/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';

const assert = require('node:assert/strict');
const {before, after, test} = require('node:test');
const {loadPlaywright, chromiumExecutablePath, isNotFoundPage} = require('./run_ui_browser_smoke');

let browser;
before(async () => {
  const {chromium} = await loadPlaywright();
  browser = await chromium.launch({headless: true, executablePath: chromiumExecutablePath()});
});
after(async () => {await browser?.close();});

for (const [name, html, expected] of [
  ['query history with a 0.404s duration', '<main><h1>GQL</h1><p>0.404s</p></main>', false],
  ['a hidden not-found surface', '<section data-testid="not-found-surface" hidden>404</section>', false],
  ['the visible not-found route', '<section data-testid="not-found-surface"><h1>404</h1></section>', true],
]) {
  test(name, async () => {
    const page = await browser.newPage();
    try {
      await page.setContent(html);
      assert.equal(await isNotFoundPage(page), expected);
    } finally {
      await page.close();
    }
  });
}
