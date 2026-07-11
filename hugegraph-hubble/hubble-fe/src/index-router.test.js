/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import fs from 'fs';
import path from 'path';

test('production router opts into validated v7 transition behavior', () => {
    const source = fs.readFileSync(path.join(__dirname, 'index.js'), 'utf8');

    expect(source).toContain('v7_startTransition: true');
    expect(source).toContain('v7_relativeSplatPath: true');
});
