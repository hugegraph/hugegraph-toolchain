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

import useDownloadJson from './useDownloadJson';

describe('useDownloadJson', () => {
    let createObjectURL;
    let revokeObjectURL;
    let anchor;

    beforeEach(() => {
        createObjectURL = jest.fn(() => 'blob:export-json');
        revokeObjectURL = jest.fn();
        global.URL.createObjectURL = createObjectURL;
        global.URL.revokeObjectURL = revokeObjectURL;

        anchor = document.createElement('a');
        jest.spyOn(anchor, 'click').mockImplementation(() => {});
        jest.spyOn(document, 'createElement').mockReturnValue(anchor);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('downloads graph data as a json file through a blob url', () => {
        const {downloadJsonHandler} = useDownloadJson();

        downloadJsonHandler('codexjson', {vertices: [], edges: []});

        expect(createObjectURL).toHaveBeenCalledWith(expect.any(Blob));
        expect(anchor.getAttribute('download')).toBe('codexjson.json');
        expect(anchor.getAttribute('href')).toBe('blob:export-json');
        expect(anchor.click).toHaveBeenCalledTimes(1);
        expect(revokeObjectURL).toHaveBeenCalledWith('blob:export-json');
    });
});
