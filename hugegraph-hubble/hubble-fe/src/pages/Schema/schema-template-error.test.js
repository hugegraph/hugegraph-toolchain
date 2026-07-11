/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

import {schemaTemplateBusinessError} from './EditLayer';

const t = (key, values) => ({key, values});

test('maps the Server duplicate-name response to an actionable local message', () => {
    expect(schemaTemplateBusinessError({
        message: 'Cannot create schema template since it has been created',
    }, t, 'create', 'existing')).toEqual({
        key: 'schema_template.create_duplicate',
        values: {name: 'existing'},
    });
});

test('uses an input-oriented fallback for other business failures', () => {
    expect(schemaTemplateBusinessError({message: ''}, t, 'create', 'new')).toEqual({
        key: 'schema_template.create_failed',
        values: undefined,
    });
    expect(schemaTemplateBusinessError({}, t, 'update', 'existing')).toEqual({
        key: 'schema_template.update_failed',
        values: undefined,
    });
});
