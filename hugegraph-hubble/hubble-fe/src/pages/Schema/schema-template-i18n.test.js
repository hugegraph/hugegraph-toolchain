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

import fs from 'fs';
import path from 'path';
import enPages from '../../i18n/resources/en-US/modules/pages.json';
import zhPages from '../../i18n/resources/zh-CN/modules/pages.json';

const sourceFiles = ['index.js', 'EditLayer.js'];

const flatten = (value, prefix = '') => Object.entries(value).flatMap(([key, child]) => {
    const current = `${prefix}${key}`;
    return child && typeof child === 'object'
        ? flatten(child, `${current}.`)
        : [[current, child]];
});

test('Schema template UI does not contain hard-coded Chinese copy', () => {
    const offenders = sourceFiles.flatMap(file => {
        const lines = fs.readFileSync(path.join(__dirname, file), 'utf8').split('\n');
        return lines.flatMap((line, index) => {
            return /[\u3400-\u9fff]/u.test(line)
                ? [`${file}:${index + 1}`]
                : [];
        });
    });

    expect(offenders).toEqual([]);
});

test('Schema template translations are symmetric and English contains no Chinese copy', () => {
    const enEntries = flatten(enPages.schema_template);
    const zhEntries = flatten(zhPages.schema_template);
    const enKeys = enEntries.map(([key]) => key);
    const zhKeys = zhEntries.map(([key]) => key);

    expect(enKeys.filter(key => !zhKeys.includes(key))).toEqual([]);
    expect(zhKeys.filter(key => !enKeys.includes(key))).toEqual([]);
    expect(enEntries.filter(([, value]) => /[\u3400-\u9fff]/u.test(value))).toEqual([]);
});
