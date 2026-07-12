/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {useCallback} from 'react';

const KeyboardAction = ({onAction, className, children, ...rest}) => {
    const handleKeyDown = useCallback(event => {
        if (event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            onAction();
        }
    }, [onAction]);

    return (
        <div
            {...rest}
            className={className}
            onClick={onAction}
            onKeyDown={handleKeyDown}
            role='button'
            tabIndex={0}
        >
            {children}
        </div>
    );
};

export default KeyboardAction;
