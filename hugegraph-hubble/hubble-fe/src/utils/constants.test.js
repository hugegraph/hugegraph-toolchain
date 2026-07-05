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

import {
    ALGORITHM_NAME,
    isAlgorithmNameMatched,
} from './constants';
import enAnalysis from '../i18n/resources/en-US/modules/analysis.json';

const t = key => key.split('.').reduce((value, part) => value && value[part], enAnalysis);

describe('isAlgorithmNameMatched', () => {
    it('matches translated display names when raw algorithm names are localized', () => {
        expect(isAlgorithmNameMatched(ALGORITHM_NAME.FINDSHORTESTPATH, 'Find', t)).toBe(true);
        expect(isAlgorithmNameMatched(ALGORITHM_NAME.PATHS, 'Basic', t)).toBe(true);
    });

    it('keeps matching raw algorithm names as a fallback', () => {
        expect(isAlgorithmNameMatched(ALGORITHM_NAME.FINDSHORTESTPATH, '查找', t)).toBe(true);
    });

    it('keeps raw fallback when callers pass translated algorithm names', () => {
        const translatedName = t('analysis.algorithm.olap.item.FINDSHORTESTPATH');

        expect(isAlgorithmNameMatched(translatedName, '查找', t)).toBe(true);
    });
});
