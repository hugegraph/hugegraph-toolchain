/*
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

import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';

import * as api from '../../../api';
import ConsoleItem from './index';

const mockMessageError = jest.fn();

jest.mock('../../../api', () => ({
    auth: {
        getDashboard: jest.fn(),
    },
}));
jest.mock('antd', () => ({
    ...jest.requireActual('antd'),
    message: {error: (...args) => mockMessageError(...args)},
}));
jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

beforeEach(() => {
    jest.clearAllMocks();
    window.fetch = jest.fn().mockResolvedValue({});
    window.open = jest.fn().mockReturnValue({
        close: jest.fn(),
        location: {replace: jest.fn()},
    });
    api.auth.getDashboard.mockResolvedValue({
        status: 200,
        data: {address: '127.0.0.1:8092', protocol: 'http'},
    });
});

test('probes the configured Dashboard only after an explicit user click', async () => {
    render(
        <MemoryRouter
            future={{
                v7_relativeSplatPath: true,
                v7_startTransition: true,
            }}
        >
            <ConsoleItem />
        </MemoryRouter>
    );

    const button = await screen.findByRole('button', {
        name: 'navigation_page.cluster_manage',
    });
    await waitFor(() => expect(button).not.toBeDisabled());
    expect(window.fetch).not.toHaveBeenCalled();

    fireEvent.click(button);

    await waitFor(() => expect(window.fetch).toHaveBeenCalledTimes(1));
    expect(window.open).toHaveBeenCalledWith('about:blank', '_blank');
    const popup = window.open.mock.results[0].value;
    await waitFor(() => expect(popup.location.replace).toHaveBeenCalledWith(
        'http://127.0.0.1:8092'
    ));
});

test('reports a blocked popup without probing the Dashboard', async () => {
    window.open.mockReturnValue(null);
    render(
        <MemoryRouter
            future={{
                v7_relativeSplatPath: true,
                v7_startTransition: true,
            }}
        >
            <ConsoleItem />
        </MemoryRouter>
    );

    const button = await screen.findByRole('button', {
        name: 'navigation_page.cluster_manage',
    });
    await waitFor(() => expect(button).not.toBeDisabled());
    fireEvent.click(button);

    expect(window.fetch).not.toHaveBeenCalled();
    expect(mockMessageError).toHaveBeenCalledTimes(1);
    expect(mockMessageError).toHaveBeenCalledWith(
        'navigation_page.dashboard_popup_blocked'
    );
});

test('shows why operations are disabled when Dashboard is unavailable', async () => {
    api.auth.getDashboard.mockResolvedValue({status: 500});
    render(
        <MemoryRouter future={{v7_relativeSplatPath: true, v7_startTransition: true}}>
            <ConsoleItem />
        </MemoryRouter>
    );

    expect(await screen.findByText('navigation_page.dashboard_unavailable'))
        .toBeInTheDocument();
});
