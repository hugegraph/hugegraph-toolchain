/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import fs from 'fs';
import path from 'path';

test('default graph flow handles arrays and owns one actionable error', () => {
    const source = fs.readFileSync(path.join(__dirname, 'index.js'), 'utf8');
    const defaultFlow = source.slice(
        source.indexOf('const setDefault ='),
        source.indexOf('const handleBack =')
    );

    expect(defaultFlow).toContain('Array.isArray(value)');
    expect(defaultFlow).toContain('defaults.some(defaultGraph => defaultGraph !== graph)');
    expect(defaultFlow).toContain('suppressBusinessErrorToast: true');
    expect(defaultFlow).not.toContain('message.error(res.message)');
});
