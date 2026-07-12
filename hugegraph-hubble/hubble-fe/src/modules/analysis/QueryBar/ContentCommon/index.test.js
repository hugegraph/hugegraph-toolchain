/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {fireEvent, render, screen} from '@testing-library/react';

import * as api from '../../../../api/index';
import GraphAnalysisContext from '../../../Context';
import ContentCommon from './index';

jest.mock('../../../../api/index', () => ({
    analysis: {addFavoriate: jest.fn().mockResolvedValue({status: 200})},
}));
jest.mock('antd', () => ({
    ...jest.requireActual('antd'),
    message: {success: jest.fn(), error: jest.fn()},
}));
jest.mock('react-i18next', () => ({
    initReactI18next: {type: '3rdParty', init: jest.fn()},
    useTranslation: () => ({t: key => key}),
}));

const renderContent = () => render(
    <GraphAnalysisContext.Provider value={{graphSpace: 'DEFAULT', graph: 'hugegraph'}}>
        <ContentCommon
            codeEditorContent='g.V()'
            setCodeEditorContent={jest.fn()}
            executeMode='QUERY'
            onExecuteModeChange={jest.fn()}
            activeTab='Gremlin'
            onExecute={jest.fn()}
            onRefresh={jest.fn()}
            isEmptyQuery={false}
            favoriteCardVisible
            setFavoriteCardVisible={jest.fn()}
        />
    </GraphAnalysisContext.Provider>
);

beforeEach(() => {
    api.analysis.addFavoriate.mockResolvedValue({status: 200});
});

test('keeps favorite submission disabled until the name is backend-compatible', () => {
    renderContent();
    const input = screen.getByPlaceholderText('analysis.query.favorite_name_placeholder');
    const submit = screen.getAllByRole('button', {name: 'analysis.query.favorite'})
        .find(button => button.closest('.ant-popover'));

    fireEvent.change(input, {target: {value: 'query-name'}});
    expect(submit).toBeDisabled();
    fireEvent.click(submit);
    expect(api.analysis.addFavoriate).not.toHaveBeenCalled();

    fireEvent.change(input, {target: {value: 'query_name'}});
    expect(submit).toBeEnabled();
    fireEvent.click(submit);
    expect(api.analysis.addFavoriate).toHaveBeenCalledTimes(1);
});
