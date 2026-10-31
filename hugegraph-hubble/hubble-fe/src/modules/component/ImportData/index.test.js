/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import {render} from '@testing-library/react';
import {Modal, Upload, message} from 'antd';
import GraphAnalysisContext from '../../Context';
import ImportData from './index';

let mockBeforeUpload;
jest.mock('antd', () => {
    const Upload = ({beforeUpload, children}) => {
        mockBeforeUpload = beforeUpload;
        return children;
    };
    Upload.LIST_IGNORE = 'LIST_IGNORE';
    return {Upload, Modal: {confirm: jest.fn()}, message: {warning: jest.fn()},
        Button: ({children}) => <button>{children}</button>, Tooltip: ({children}) => children};
});
jest.mock('react-i18next', () => ({useTranslation: () => ({t: key => key})}));
jest.mock('../../../api', () => ({analysis: {getUploadList: jest.fn(() => '/import')}}));

afterEach(() => jest.clearAllMocks());

test('requires confirmation and cancels upload if the graph changes', async () => {
    const view = render(<GraphAnalysisContext.Provider value={{graphSpace: 'DEFAULT', graph: 'one'}}>
        <ImportData buttonEnable onUploadChange={jest.fn()} />
    </GraphAnalysisContext.Provider>);

    const cancelled = mockBeforeUpload();
    Modal.confirm.mock.calls.at(-1)[0].onCancel();
    expect(await cancelled).toBe(Upload.LIST_IGNORE);

    const accepted = mockBeforeUpload();
    Modal.confirm.mock.calls.at(-1)[0].onOk();
    expect(await accepted).toBe(true);

    const stale = mockBeforeUpload();
    view.rerender(<GraphAnalysisContext.Provider value={{graphSpace: 'DEFAULT', graph: 'two'}}>
        <ImportData buttonEnable onUploadChange={jest.fn()} />
    </GraphAnalysisContext.Provider>);
    Modal.confirm.mock.calls.at(-1)[0].onOk();
    expect(await stale).toBe(Upload.LIST_IGNORE);
    expect(message.warning).toHaveBeenCalledWith('analysis.canvas.import_scope_changed');

    const abandoned = mockBeforeUpload();
    view.unmount();
    Modal.confirm.mock.calls.at(-1)[0].onOk();
    expect(await abandoned).toBe(Upload.LIST_IGNORE);
});
