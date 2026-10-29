/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
'use strict';
const test = require('node:test');
const assert = require('node:assert/strict');
const {before, after} = test;
const {loadPlaywright, chromiumExecutablePath} = require('./run_ui_browser_smoke');
const {assertUsableControl, assertNoHorizontalOverflow} = require('./ui_usability');
let browser;
before(async () => {
    const {chromium} = await loadPlaywright();
    browser = await chromium.launch({headless: true, executablePath: chromiumExecutablePath(), args: ['--no-sandbox']});
});
after(async () => {await browser?.close();});
async function page(t, html) {
    const value = await browser.newPage({viewport: {width: 800, height: 600}});
    t.after(() => value.close());
    await value.setContent(html);
    return value;
}
const button = '<button style="width:120px;height:40px;border-radius:24px">Run query</button>';
test('rounded controls remain usable after scrolling into view', async t => {
    const p = await page(t, '<div style="height:900px"></div>'+button);
    await assertUsableControl(p.getByRole('button'));
    await assertNoHorizontalOverflow(p);
});
test('a covering overlay is rejected', async t => {
    const p = await page(t, button+'<div style="position:fixed;inset:0;background:white"></div>');
    await assert.rejects(assertUsableControl(p.getByRole('button')), /covered/);
});
test('ancestor overflow clipping is rejected', async t => {
    const p = await page(t, '<div style="height:20px;overflow:hidden">'+button+'</div>');
    await assert.rejects(assertUsableControl(p.getByRole('button')), /clipped/);
});
test('clipped button text is rejected', async t => {
    const p = await page(t, '<button style="height:20px;width:50px;overflow:hidden">A long query action</button>');
    await assert.rejects(assertUsableControl(p.getByRole('button')), /clipped/);
});
test('document horizontal overflow is rejected', async t => {
    const p = await page(t, '<div style="width:1200px">Overflow</div>');
    await assert.rejects(assertNoHorizontalOverflow(p), /horizontal overflow/);
});
