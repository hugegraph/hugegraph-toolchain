/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import fs from 'fs';
import path from 'path';

const DELETE_TABLES = ['Property', 'Vertex', 'Edge'];

test.each(DELETE_TABLES)('%s deletion owns errors without exposing raw responses', table => {
    const source = fs.readFileSync(path.join(__dirname, table, 'index.js'), 'utf8');

    expect(source).not.toContain('message.error(res.message)');
    expect(source).toContain("message.error(t('schema.delete_failed'))");
    expect(source).toContain('suppressBusinessErrorToast: true');
    expect(source).toContain('.catch(() =>');
});
