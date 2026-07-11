/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */

import React from 'react';
import {Button, Result} from 'antd';

class RouteErrorBoundary extends React.Component {
    constructor(props) {
        super(props);
        this.state = {failed: false};
        this.reload = this.reload.bind(this);
    }

    static getDerivedStateFromError() {
        return {failed: true};
    }

    componentDidCatch(error, info) {
        console.error('Route render failed', error, info);
    }

    reload() {
        window.location.reload();
    }

    render() {
        if (this.state.failed) {
            return (
                <Result
                    status='error'
                    title='Page failed to load'
                    extra={<Button onClick={this.reload}>Reload</Button>}
                />
            );
        }
        return this.props.children;
    }
}

export default RouteErrorBoundary;
