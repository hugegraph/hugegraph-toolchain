/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {useCallback, useState} from 'react';
import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import KeyboardAction from './index';

const ExpandableAction = () => {
    const [expanded, setExpanded] = useState(false);
    const toggle = useCallback(() => setExpanded(value => !value), []);

    return (
        <KeyboardAction
            aria-expanded={expanded}
            onAction={toggle}
        >
            Toggle details
        </KeyboardAction>
    );
};

test('exposes state and supports Enter and Space activation', async () => {
    render(<ExpandableAction />);

    const action = screen.getByRole('button', {name: 'Toggle details'});
    expect(action).toHaveAttribute('aria-expanded', 'false');

    action.focus();
    await userEvent.keyboard('{Enter}');
    expect(action).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard(' ');
    expect(action).toHaveAttribute('aria-expanded', 'false');
});
