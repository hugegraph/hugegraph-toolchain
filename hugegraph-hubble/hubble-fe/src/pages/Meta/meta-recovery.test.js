/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {fireEvent, render, screen, waitFor} from '@testing-library/react';
import Meta from './index';
import * as api from '../../api';

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

jest.mock('react-router-dom', () => ({
    useNavigate: () => jest.fn(),
    useParams: () => ({graphspace: 'space-a', graph: 'graph-a'}),
}));

jest.mock('../../api', () => ({
    manage: {
        getGraph: jest.fn(),
        getGraphSpace: jest.fn(),
    },
}));

jest.mock('./ListView', () => () => <div>schema list</div>);
jest.mock('./ImageView', () => () => <div>schema image</div>);
jest.mock('../../components/GraphJourneyNav', () => () => <nav>journey</nav>);

beforeEach(() => {
    jest.clearAllMocks();
    window.matchMedia = jest.fn().mockImplementation(query => ({
        matches: false,
        media: query,
        addListener: jest.fn(),
        removeListener: jest.fn(),
    }));
});

test('shows persistent source-specific recovery instead of spinning forever', async () => {
    api.manage.getGraph.mockRejectedValueOnce(new Error('graph down')).mockResolvedValueOnce({
        status: 200,
        data: {nickname: 'Graph A'},
    });
    api.manage.getGraphSpace.mockRejectedValueOnce(new Error('space down')).mockResolvedValueOnce({
        status: 200,
        data: {nickname: 'Space A'},
    });

    render(<Meta />);

    expect(await screen.findByText('schema.identity.graph_unavailable')).toBeInTheDocument();
    expect(screen.getByText('schema.identity.graphspace_unavailable')).toBeInTheDocument();
    expect(screen.queryByText('schema list')).not.toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', {name: 'schema.identity.retry'}));

    await waitFor(() => expect(screen.getByText('Space A - Graph A - schema.title'))
        .toBeInTheDocument());
    expect(screen.getByText('schema list')).toBeInTheDocument();
});
