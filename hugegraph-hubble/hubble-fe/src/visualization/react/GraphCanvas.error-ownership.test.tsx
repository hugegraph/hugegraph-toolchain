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

import '@testing-library/jest-dom';
import {act, render, screen} from '@testing-library/react';
import {GraphCanvas} from './GraphCanvas';
import {createGraphController} from '../runtime';
import type {GraphData} from '../model/types';
import type {ControllerOptions, ControllerSnapshot} from '../runtime/types';

jest.mock('../runtime', () => ({createGraphController: jest.fn()}));

const factory = jest.mocked(createGraphController);
const data: GraphData = {nodes: [], edges: []};

beforeEach(() => {
    jest.clearAllMocks();
    global.ResizeObserver = class {
        observe = jest.fn();
        unobserve = jest.fn();
        disconnect = jest.fn();
    } as unknown as typeof ResizeObserver;
});

test('a newer snapshot error replaces and then clears the synchronized local renderer error', async () => {
    let snapshot: ControllerSnapshot = {
        status: 'ready', data, canUndo: false, canRedo: false,
        selectedIds: [], hoveredIds: [],
    };
    const listeners = new Set<() => void>();
    let options: ControllerOptions | undefined;
    const controller = {
        replaceData: jest.fn().mockResolvedValue({status: 'applied', value: undefined}),
        setLayout: jest.fn().mockResolvedValue(undefined),
        resize: jest.fn().mockResolvedValue(undefined),
        fitView: jest.fn().mockResolvedValue(undefined),
        dispose: jest.fn().mockResolvedValue(undefined),
        getSnapshot: () => snapshot,
        subscribe: jest.fn((listener: () => void) => {
            listeners.add(listener);
            return () => listeners.delete(listener);
        }),
    };
    factory.mockImplementation((_surface, config = {}) => {
        options = config;
        return controller as unknown as ReturnType<typeof createGraphController>;
    });

    render(<GraphCanvas data={data} />);
    await act(async () => {});

    const rendererFailure = new Error('old renderer failure');
    act(() => options?.onError?.(rendererFailure));
    expect(screen.getByRole('alert')).toHaveTextContent('old renderer failure');

    const latestFailure = new Error('latest apply-style failure');
    act(() => {
        snapshot = {...snapshot, status: 'error', error: latestFailure} as ControllerSnapshot;
        listeners.forEach(listener => listener());
    });
    expect(screen.getByRole('alert')).toHaveTextContent('latest apply-style failure');
    expect(screen.getByRole('alert')).not.toHaveTextContent('old renderer failure');

    act(() => {
        snapshot = {...snapshot, status: 'ready', error: undefined} as ControllerSnapshot;
        listeners.forEach(listener => listener());
    });
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
});
