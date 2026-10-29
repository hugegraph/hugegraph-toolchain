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
const assert = require('node:assert/strict');

async function assertUsableControl(control) {
    await control.scrollIntoViewIfNeeded();
    assert.ok(await control.isVisible() && await control.isEnabled(), 'Key control must be visible and enabled');
    const problem = await control.evaluate(element => {
        const box = element.getBoundingClientRect();
        if (!box.width || !box.height) return 'Key control has no visible area';
        for (let node = element; node; node = node.parentElement) {
            if (Number(getComputedStyle(node).opacity) === 0) return 'Key control is visually transparent';
        }
        const inside = (outer, x, y) => (!x || box.left >= outer.left - 1 && box.right <= outer.right + 1)
            && (!y || box.top >= outer.top - 1 && box.bottom <= outer.bottom + 1);
        if (!inside({left: 0, top: 0, right: innerWidth, bottom: innerHeight}, true, true)) return 'Key control is outside viewport';
        for (let ancestor = element.parentElement; ancestor; ancestor = ancestor.parentElement) {
            const style = getComputedStyle(ancestor), rect = ancestor.getBoundingClientRect();
            const clip = {left: rect.left + ancestor.clientLeft, top: rect.top + ancestor.clientTop,
                right: rect.left + ancestor.clientLeft + ancestor.clientWidth,
                bottom: rect.top + ancestor.clientTop + ancestor.clientHeight};
            if (!inside(clip, style.overflowX !== 'visible', style.overflowY !== 'visible')) return 'Key control is clipped by an ancestor';
        }
        for (const child of [element, ...element.querySelectorAll('*')]) {
            if (child.clientWidth > 0 && (child.scrollWidth > child.clientWidth + 1
                || child.scrollHeight > child.clientHeight + 1)) return 'Key control content is clipped';
        }
        // Center-line points stay inside rounded buttons while catching a partial overlay.
        for (const fraction of [.25, .5, .75]) {
            const hit = document.elementFromPoint(box.left + box.width * fraction, box.top + box.height / 2);
            if (!hit || !element.contains(hit)) return 'Key control is covered';
        }
        return null;
    });
    assert.equal(problem, null, problem || undefined);
    await control.click({trial: true});
}
async function assertNoHorizontalOverflow(page) {
    const size = await page.evaluate(() => ({viewport: document.documentElement.clientWidth,
        content: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth)}));
    assert.ok(size.content <= size.viewport + 1, 'Document has horizontal overflow');
}
module.exports = {assertUsableControl, assertNoHorizontalOverflow};
