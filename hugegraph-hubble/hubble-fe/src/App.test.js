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

import {act, render, screen, waitFor} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import App from './App';
import * as api from './api';

jest.mock('./api', () => ({
    config: {
        getConfig: jest.fn(),
    },
}));

jest.mock('./routes', () => ({element}) => (
    <div
        data-testid="app-route"
        data-auth-enabled={String(
            jest.requireActual('./utils/config').isAuthEnabled()
        )}
    >
        {element}
    </div>
));

jest.mock('./layout.ant', () => () => <div>Hubble layout</div>);
jest.mock('./auth/AuthContext', () => ({
    AuthContextProvider: ({children}) => children,
}));

test('wires the Hubble layout into the application router', async () => {
    sessionStorage.clear();
    api.config.getConfig.mockResolvedValue({
        status: 200,
        data: {pd_enabled: false, auth_enabled: false},
    });
    render(
        <MemoryRouter
            future={{v7_startTransition: true, v7_relativeSplatPath: true}}
        >
            <App />
        </MemoryRouter>
    );
    expect(await screen.findByTestId('app-route')).toBeInTheDocument();
    expect(screen.getByText('Hubble layout')).toBeInTheDocument();
    expect(JSON.parse(sessionStorage.getItem('hubble_config_'))).toEqual({pd_enabled: false, auth_enabled: false});
});

test('shows a retry surface when configuration bootstrap fails', async () => {
    api.config.getConfig.mockRejectedValue(new Error('offline'));

    render(
        <MemoryRouter
            future={{v7_startTransition: true, v7_relativeSplatPath: true}}
        >
            <App />
        </MemoryRouter>
    );

    expect(await screen.findByRole('alert')).toHaveTextContent(
        'Unable to load Hubble configuration.'
    );
    expect(screen.getByRole('button', {name: 'Retry'})).toBeInTheDocument();
});

test('revalidates fail-closed server capabilities until verified', async () => {
    jest.useFakeTimers();
    api.config.getConfig
        .mockResolvedValueOnce({
            status: 200,
            data: {
                pd_enabled: false,
                auth_enabled: true,
                server_capabilities_verified: false,
            },
        })
        .mockResolvedValueOnce({
            status: 200,
            data: {
                pd_enabled: false,
                auth_enabled: false,
                server_capabilities_verified: true,
            },
        });

    render(
        <MemoryRouter
            future={{v7_startTransition: true, v7_relativeSplatPath: true}}
        >
            <App />
        </MemoryRouter>
    );
    expect(await screen.findByTestId('app-route')).toBeInTheDocument();
    expect(screen.getByTestId('app-route'))
        .toHaveAttribute('data-auth-enabled', 'true');

    await act(async () => {
        jest.advanceTimersByTime(2000);
    });
    await waitFor(() => expect(api.config.getConfig).toHaveBeenCalledTimes(2));
    expect(JSON.parse(sessionStorage.getItem('hubble_config_')))
        .toMatchObject({
            auth_enabled: false,
            server_capabilities_verified: true,
        });
    expect(screen.getByTestId('app-route'))
        .toHaveAttribute('data-auth-enabled', 'false');
    jest.useRealTimers();
});
