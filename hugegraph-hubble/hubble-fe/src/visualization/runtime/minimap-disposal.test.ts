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

import {GraphEvent, Minimap} from '@antv/g6';

// Keep the published plugin and its debounce/throttle real. Delayed disposal
// needs only an emitter and model boundary; no canvas is initialized here.
const createMinimap = () => {
    const listeners = new Map<string, (event: unknown) => void>();
    const graph = {
        on: jest.fn((event: string, listener: (event: unknown) => void) => listeners.set(event, listener)),
        off: jest.fn((event: string, listener: (event: unknown) => void) => {
            if (listeners.get(event) === listener) listeners.delete(event);
        }),
    };
    const model = {getData: jest.fn(() => ({nodes: [], edges: [], combos: []}))};
    const plugin = new Minimap({graph, model} as unknown as ConstructorParameters<typeof Minimap>[0],
        {type: 'minimap'});
    const emit = (event: GraphEvent) => {
        const listener = listeners.get(event);
        expect(listener).toBeDefined();
        listener!({type: event, data: {render: false}});
    };
    return {plugin, model, emit};
};

beforeEach(() => { jest.useFakeTimers(); });
afterEach(() => {
    jest.clearAllTimers();
    jest.useRealTimers();
    jest.restoreAllMocks();
});

test.each([GraphEvent.AFTER_RENDER, GraphEvent.AFTER_LAYOUT, GraphEvent.AFTER_DRAW])(
    'pending minimap %s refresh cannot read the model after disposal', event => {
        const {plugin, model, emit} = createMinimap();
        try {
            emit(event);
            expect(jest.getTimerCount()).toBeGreaterThan(0);
            plugin.destroy();
            expect(() => jest.advanceTimersByTime(128)).not.toThrow();
            expect(model.getData).not.toHaveBeenCalled();
        }
        finally { if (!plugin.destroyed) plugin.destroy(); }
    }
);

test('a live minimap still propagates model failures from its delayed refresh', () => {
    const {plugin, model, emit} = createMinimap();
    const failure = new Error('model read failed');
    model.getData.mockImplementation(() => { throw failure; });
    try {
        emit(GraphEvent.AFTER_RENDER);
        expect(() => jest.advanceTimersByTime(128)).toThrow(failure);
        expect(model.getData).toHaveBeenCalledTimes(1);
    }
    finally { plugin.destroy(); }
});

test('a pending minimap transform cannot update the old camera after disposal', () => {
    const {plugin, emit} = createMinimap();
    const cameraWrite = jest.fn();
    // Test-only bridge: the real throttled callback dynamically calls this
    // private method. Observe camera writes without constructing a canvas.
    const bridge = plugin as unknown as {setCamera(): void};
    jest.spyOn(bridge, 'setCamera').mockImplementation(cameraWrite);
    try {
        emit(GraphEvent.AFTER_TRANSFORM);
        expect(cameraWrite).toHaveBeenCalledTimes(1);
        emit(GraphEvent.AFTER_TRANSFORM);
        expect(jest.getTimerCount()).toBeGreaterThan(0);
        plugin.destroy();
        expect(() => jest.advanceTimersByTime(32)).not.toThrow();
        expect(cameraWrite).toHaveBeenCalledTimes(1);
    }
    finally { if (!plugin.destroyed) plugin.destroy(); }
});
