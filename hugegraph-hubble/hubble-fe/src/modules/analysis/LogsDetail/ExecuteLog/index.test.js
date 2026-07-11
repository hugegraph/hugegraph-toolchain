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

import {failureReasonDescription} from './index';

describe('Gremlin execution history failure reason', () => {
    const t = key => ({
        'analysis.logs.failure_reason.GREMLIN_EXECUTION_FAILED':
            'Query failed. Review the statement and try again.',
    })[key];

    test('maps a controlled reason code to actionable localized text', () => {
        expect(failureReasonDescription(
            {status: 'FAILED', failure_reason: 'GREMLIN_EXECUTION_FAILED'}, t
        )).toBe('Query failed. Review the statement and try again.');
    });

    test('does not show a reason for successful history', () => {
        expect(failureReasonDescription(
            {status: 'SUCCESS', failure_reason: 'GREMLIN_EXECUTION_FAILED'}, t
        )).toBeNull();
    });

    test('does not expose unknown backend values', () => {
        expect(failureReasonDescription(
            {status: 'FAILED', failure_reason: 'raw Groovy signature'}, t
        )).toBeNull();
    });
});
