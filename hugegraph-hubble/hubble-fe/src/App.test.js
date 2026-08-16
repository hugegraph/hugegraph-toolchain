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
import {MemoryRouter} from 'react-router-dom';
import App from './App';
import * as api from './api';

jest.mock('./api', () => ({
    config: {
        getConfig: jest.fn(),
    },
}));

jest.mock('./routes', () => ({element}) => (
    <div data-testid="app-route">{element}</div>
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
    expect(JSON.parse(sessionStorage.getItem('hubble_config_')))
        .toEqual({pd_enabled: false, auth_enabled: false});
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
