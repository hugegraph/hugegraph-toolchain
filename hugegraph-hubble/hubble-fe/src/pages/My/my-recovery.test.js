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

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import My from './index';
import * as api from '../../api';

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

jest.mock('../../api', () => ({
    auth: {
        getPersonal: jest.fn(),
        updatePwd: jest.fn(),
    },
}));

jest.mock('./EditLayer', () => ({refresh}) => (
    <button type='button' onClick={refresh}>mock profile saved</button>
));

jest.mock('../../utils/rules', () => ({
    required: () => ({required: true}),
}));

beforeEach(() => {
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        addListener: jest.fn(),
        removeListener: jest.fn(),
    }));
});

const deferred = () => {
    let resolve;
    const promise = new Promise(resolvePromise => {
        resolve = resolvePromise;
    });
    return {promise, resolve};
};

test('shows a persistent profile error and retries without stale identity', async () => {
    api.auth.getPersonal
        .mockRejectedValueOnce(new Error('down'))
        .mockResolvedValueOnce({
            status: 200,
            data: {user_name: 'admin', user_nickname: 'Administrator'},
        });

    render(<My />);

    expect(await screen.findByText('my.load.unavailable')).toBeInTheDocument();
    expect(screen.queryByText('Administrator')).not.toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', {name: 'my.load.retry'}));

    expect(await screen.findByText('Administrator')).toBeInTheDocument();
    expect(screen.queryByText('my.load.unavailable')).not.toBeInTheDocument();
});

test('ignores an old profile response after a newer refresh', async () => {
    const oldRequest = deferred();
    api.auth.getPersonal
        .mockReturnValueOnce(oldRequest.promise)
        .mockResolvedValueOnce({
            status: 200,
            data: {user_name: 'admin', user_nickname: 'New profile'},
        });

    render(<My />);

    expect(screen.getByRole('button', {name: 'common.action.edit'})).toBeDisabled();
    await userEvent.click(screen.getByRole('button', {name: 'mock profile saved'}));
    expect(await screen.findByText('New profile')).toBeInTheDocument();

    oldRequest.resolve({
        status: 200,
        data: {user_name: 'admin', user_nickname: 'Old profile'},
    });

    expect(await screen.findByText('New profile')).toBeInTheDocument();
    expect(screen.queryByText('Old profile')).not.toBeInTheDocument();
});
