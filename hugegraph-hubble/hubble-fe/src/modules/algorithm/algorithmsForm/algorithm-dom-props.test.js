/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0.
 */

import fs from 'fs';
import path from 'path';

const collectJavaScript = directory => fs.readdirSync(directory, {withFileTypes: true})
    .flatMap(entry => {
        const entryPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            return collectJavaScript(entryPath);
        }
        return entry.name.endsWith('.js') && !entry.name.endsWith('.test.js') ? [entryPath] : [];
    });

test('algorithm component props are not forwarded to Collapse panel DOM', () => {
    const offenders = collectJavaScript(__dirname).flatMap(file => {
        const source = fs.readFileSync(file, 'utf8');
        return /<Collapse\.Panel[\s\S]*?\{\.\.\.props\}/u.test(source)
            ? [path.relative(__dirname, file)]
            : [];
    });

    expect(offenders).toEqual([]);
});
