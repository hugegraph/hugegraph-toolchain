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
import OltpFormHome from './index';

jest.mock('../Oltp/OltpItem', () => ({algorithmName}) => <div>{algorithmName}</div>);
jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));

test('explains when the selected OLTP catalog has no search match', () => {
    const view = render(<OltpFormHome search='no-algorithm-has-this-name' />);
    expect(screen.getByText('query_result.empty')).toBeVisible();
    view.rerender(<OltpFormHome search='' />);
    expect(screen.queryByText('query_result.empty')).not.toBeInTheDocument();
});

test('treats an OLAP-only search as empty within the OLTP catalog', () => {
    render(<OltpFormHome search='PageRank' />);
    expect(screen.getByText('query_result.empty')).toBeVisible();
});
