/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {InputColorSelect} from './index';

jest.mock('react-i18next', () => ({
    useTranslation: () => ({t: key => key}),
}));

jest.mock('react-color', () => ({
    ChromePicker: () => <div>picker content</div>,
}));

test('opens and closes the color picker with keyboard state and focus recovery', async () => {
    render(<InputColorSelect />);

    const trigger = screen.getByRole('button', {name: 'style_config.color_picker'});
    expect(trigger).toHaveAttribute('aria-expanded', 'false');

    trigger.focus();
    await userEvent.keyboard('{Enter}');
    expect(screen.getByText('picker content')).toBeInTheDocument();
    expect(trigger).toHaveAttribute('aria-expanded', 'true');

    await userEvent.keyboard('{Escape}');
    expect(screen.queryByText('picker content')).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
});
