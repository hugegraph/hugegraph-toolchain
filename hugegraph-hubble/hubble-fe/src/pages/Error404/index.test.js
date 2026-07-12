/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {render, screen} from '@testing-library/react';
import {MemoryRouter} from 'react-router-dom';
import Error404 from './index';

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

test('uses localized unknown-route recovery copy', () => {
    render(
        <MemoryRouter future={{v7_startTransition: true, v7_relativeSplatPath: true}}>
            <Error404 />
        </MemoryRouter>
    );

    expect(screen.getByText('not_found.subtitle')).toBeInTheDocument();
    expect(screen.getByRole('link', {name: 'not_found.home'})).toHaveAttribute('href', '/');
});
