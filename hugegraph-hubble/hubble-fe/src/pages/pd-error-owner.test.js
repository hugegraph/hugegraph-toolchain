/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import fs from 'fs';
import path from 'path';

const files = [
    'GraphSpace/index.js',
    'GraphSpace/EditLayer.js',
    'Schema/index.js',
    'Schema/EditLayer.js',
    'Account/index.js',
    'Account/EditLayer.js',
];

test.each(files)('%s owns one localized error for page requests', relativePath => {
    const source = fs.readFileSync(path.join(__dirname, relativePath), 'utf8');

    expect(source).toContain('suppressBusinessErrorToast: true');
    expect(source).toContain('.catch(');
    expect(source).not.toContain('message.error(res.message)');
});
