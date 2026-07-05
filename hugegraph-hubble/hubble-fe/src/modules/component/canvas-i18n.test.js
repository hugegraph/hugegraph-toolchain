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

import enAnalysis from '../../i18n/resources/en-US/modules/analysis.json';

const get = path => path.split('.').reduce((value, key) => value && value[key], enAnalysis);
const hasChinese = value => /[\u4e00-\u9fff]/.test(value);

describe('canvas i18n coverage', () => {
    it('provides English labels for graph canvas panels used by algorithm pages', () => {
        const requiredKeys = [
            'analysis.canvas.canvas_3d.node',
            'analysis.canvas.canvas_3d.edge',
            'analysis.canvas.canvas_3d.tip',
            'analysis.canvas.filter_drawer.title',
            'analysis.canvas.filter_drawer.add_property',
            'analysis.canvas.setting_panel.title',
            'analysis.canvas.layout_panel.title',
            'analysis.canvas.layout_panel.force',
            'analysis.canvas.statistics_panel.label_statistics',
            'analysis.canvas.statistics_panel.graph_statistics',
            'analysis.canvas.statistics_panel.highlight',
            'analysis.canvas.dynamic_add.vertex_type',
            'analysis.canvas.edit_element.edit_details',
        ];

        requiredKeys.forEach(key => {
            const value = get(key);

            expect(typeof value).toBe('string');
            expect(value.length).toBeGreaterThan(0);
            expect(hasChinese(value)).toBe(false);
        });
    });
});
