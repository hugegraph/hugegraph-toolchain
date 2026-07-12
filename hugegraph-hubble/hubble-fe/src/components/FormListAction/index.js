/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {Button} from 'antd';
import {useCallback} from 'react';

const FormListRemove = ({remove, index, afterRemove, children}) => {
    const handleClick = useCallback(() => {
        remove(index);
        afterRemove?.();
    }, [afterRemove, index, remove]);

    return <Button type='link' onClick={handleClick}>{children}</Button>;
};

const FormListAdd = ({add, children}) => {
    const handleClick = useCallback(() => add(), [add]);

    return (
        <Button type='link' className='form_attr_add' onClick={handleClick}>
            {children}
        </Button>
    );
};

export {FormListAdd, FormListRemove};
