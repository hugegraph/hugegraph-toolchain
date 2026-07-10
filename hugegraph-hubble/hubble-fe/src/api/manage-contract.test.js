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

import request from './request';
import * as manage from './manage';

jest.mock('./request', () => ({
    get: jest.fn(),
    put: jest.fn(),
}));

beforeEach(() => {
    jest.clearAllMocks();
});

test('updates a graph with PUT JSON on the canonical route', () => {
    manage.updateGraph('DEFAULT', 'g', {nickname: 'nick'});
    expect(request.put).toHaveBeenCalledWith(
        '/graphspaces/DEFAULT/graphs/g',
        {nickname: 'nick'},
    );
});

test('reads the default graph from the canonical route', () => {
    manage.getDefaultGraph('DEFAULT');
    expect(request.get).toHaveBeenCalledWith(
        'graphspaces/DEFAULT/graphs/default',
    );
});

test('does not expose default GraphSpace mutation facades', () => {
    expect(manage.setDefaultGraphSpace).toBeUndefined();
    expect(manage.getDefaultGraphSpace).toBeUndefined();
});
