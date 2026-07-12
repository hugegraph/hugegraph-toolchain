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

import {useState} from 'react';
import {fireEvent, render, screen, within} from '@testing-library/react';
import QueryBar, {isFavoritePopoverOpen} from './index';

jest.mock('../../../../components/CodeEditor', () => () => <div>editor</div>);
jest.mock('../ContentCommon', () => ({
    children,
    favoriteCardVisible,
    setFavoriteCardVisible,
}) => (
    <div>
        <button onClick={() => setFavoriteCardVisible(true)}>open favorite</button>
        <span>{favoriteCardVisible ? 'favorite open' : 'favorite closed'}</span>
        {children}
    </div>
));
jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => ({
        'analysis.query.gremlin_tab': 'Gremlin',
        'analysis.query.cypher_tab': 'Cypher',
    })[key] || key}),
}));

beforeAll(() => {
    window.matchMedia = window.matchMedia || (() => ({
        matches: false,
        addListener: jest.fn(),
        removeListener: jest.fn(),
    }));
});

it('does not transfer an open favorite popover when query tabs change', () => {
    expect(isFavoritePopoverOpen(true, 'Gremlin', 'Gremlin')).toBe(true);
    expect(isFavoritePopoverOpen(true, 'Cypher', 'Gremlin')).toBe(false);
    expect(isFavoritePopoverOpen(false, 'Gremlin', 'Gremlin')).toBe(false);

    const ControlledQueryBar = () => {
        const [activeTab, setActiveTab] = useState('Gremlin');
        return (
            <QueryBar
                activeTab={activeTab}
                onTabsChange={setActiveTab}
                codeEditorContent='g.V()'
                setCodeEditorContent={jest.fn()}
            />
        );
    };

    render(<ControlledQueryBar />);
    fireEvent.click(screen.getByRole('button', {name: 'open favorite'}));
    expect(screen.getByText('favorite open')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('tab', {name: 'Cypher'}));
    expect(within(screen.getByRole('tabpanel', {name: 'Cypher'}))
        .getByText('favorite closed')).toBeInTheDocument();
});
