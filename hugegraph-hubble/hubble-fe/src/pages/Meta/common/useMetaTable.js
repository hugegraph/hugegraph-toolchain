/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */

import {useCallback, useEffect, useRef, useState} from 'react';

const useMetaTable = (fetchPage, {identityKey = '', refreshKey = ''} = {}) => {
    const [data, setData] = useState([]);
    const [pagination, setPagination] = useState({current: 1, total: 0});
    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(false);
    const requestToken = useRef(null);
    const currentPage = pagination.current;
    const resetKey = `${identityKey}:${refreshKey}`;
    const previousResetKey = useRef(resetKey);

    const load = useCallback((requestedPage = currentPage) => {
        const token = Symbol('meta-table');
        requestToken.current = token;
        setData([]);
        setError(false);
        setLoading(true);
        fetchPage({page_no: requestedPage}).then(res => {
            if (requestToken.current !== token) {
                return;
            }
            if (res.status !== 200) {
                setError(true);
                return;
            }
            const lastPage = Math.max(1, Math.ceil(res.data.total / 10));
            if (res.data.records.length === 0 && res.data.total > 0
                && requestedPage > lastPage) {
                setPagination(current => ({
                    ...current, current: lastPage, total: res.data.total,
                }));
                return;
            }
            setData(res.data.records);
            setPagination(current => ({...current, total: res.data.total}));
        }).catch(() => {
            if (requestToken.current === token) {
                setError(true);
            }
        }).finally(() => {
            if (requestToken.current === token) {
                setLoading(false);
            }
        });
    }, [currentPage, fetchPage]);

    useEffect(() => {
        const contextChanged = previousResetKey.current !== resetKey;
        previousResetKey.current = resetKey;
        if (contextChanged && currentPage !== 1) {
            requestToken.current = null;
            setPagination(current => ({...current, current: 1}));
            return undefined;
        }
        load(currentPage);
        return () => {
            requestToken.current = null;
        };
    }, [currentPage, load, resetKey]);

    const handleTable = useCallback(next => setPagination(current => ({
        ...current,
        current: next.current,
    })), []);

    return {data, pagination, loading, error, retry: load, handleTable};
};

export default useMetaTable;
