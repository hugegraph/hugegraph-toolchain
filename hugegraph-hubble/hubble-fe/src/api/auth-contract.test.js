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

import request from './request';
import * as auth from './auth';

jest.mock('./request', () => ({
    get: jest.fn(),
    post: jest.fn(),
    put: jest.fn(),
    delete: jest.fn(),
}));

beforeEach(() => {
    jest.clearAllMocks();
});

test.each([
    ['getAllUserList', [{page_no: 1}], 'get', '/auth/users'],
    ['getUserInfo', ['user'], 'get', '/auth/users/user'],
    ['addUser', [{user_name: 'user'}], 'post', '/auth/users'],
    ['updateUser', ['user', {user_nickname: 'User'}], 'put', '/auth/users/user'],
    ['updateAdminspace', ['user', ['SPACE']], 'post',
        '/auth/users/updateadminspace/user'],
    ['delUser', ['user'], 'delete', '/auth/users/user'],
])('%s forwards page-owned error controls', (method, args, verb, route) => {
    const config = {suppressBusinessErrorToast: true};
    auth[method](...args, config);

    if (verb === 'get' && method === 'getAllUserList') {
        expect(request.get).toHaveBeenCalledWith(route, {params: args[0], ...config});
        return;
    }
    if (verb === 'delete') {
        expect(request[verb]).toHaveBeenCalledWith(route, undefined, config);
        return;
    }
    if (verb === 'get') {
        expect(request[verb]).toHaveBeenCalledWith(route, config);
        return;
    }
    expect(request[verb]).toHaveBeenCalledWith(route, args.at(-1), config);
});
