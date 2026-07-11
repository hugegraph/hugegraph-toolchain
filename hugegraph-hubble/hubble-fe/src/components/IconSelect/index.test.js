/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 */

import fs from 'fs';
import path from 'path';

test('disabled icon selector uses localized placeholder without Chinese copy', () => {
    const source = fs.readFileSync(path.join(__dirname, 'index.js'), 'utf8');

    expect(source).not.toMatch(/[\u3400-\u9fff]/u);
    expect(source).toContain("placeholder={t('selector.placeholder')}");
});
