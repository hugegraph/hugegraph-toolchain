/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 */

import fs from 'fs';
import path from 'path';

const dropdownSources = [
    'pages/Role/index.js',
    'pages/Graph/Card.js',
    'pages/GraphSpace/Card.js',
    'modules/analysis/QueryBar/ContentCommon/index.js',
    'modules/component/NewConfig/index.js',
    'modules/component/ExportData/index.js',
];

test('reachable Dropdowns use the Ant Design menu API', () => {
    const offenders = dropdownSources.filter(file => {
        const source = fs.readFileSync(path.join(__dirname, file), 'utf8');
        return /<Dropdown(?:\.Button)?\b[\s\S]*?\boverlay\s*=/u.test(source);
    });

    expect(offenders).toEqual([]);
});
