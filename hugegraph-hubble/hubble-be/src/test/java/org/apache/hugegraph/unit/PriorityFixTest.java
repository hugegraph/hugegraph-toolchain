/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership.
 * Licensed under the Apache License, Version 2.0.
 */
package org.apache.hugegraph.unit;

import java.util.Arrays;

import org.apache.hugegraph.driver.HugeClient;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.handler.CustomInterceptor;
import org.apache.hugegraph.util.PageUtil;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.baomidou.mybatisplus.core.metadata.IPage;

public class PriorityFixTest {

    @Test
    public void testAfterCompletionClosesRequestClient() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        HugeClient client = Mockito.mock(HugeClient.class);
        request.setAttribute("hugeClient", client);

        new CustomInterceptor().afterCompletion(request,
                                                new MockHttpServletResponse(),
                                                new Object(), null);

        Mockito.verify(client).close();
        Assert.assertNull(request.getAttribute("hugeClient"));
    }

    @Test
    public void testNewPageCalculatesPageCount() {
        IPage<Integer> page = PageUtil.newPage(Arrays.asList(3, 4), 2, 2, 5);

        Assert.assertEquals(3L, page.getPages());
        Assert.assertEquals(5L, page.getTotal());
        Assert.assertEquals(Arrays.asList(3, 4), page.getRecords());
    }

    @Test
    public void testPageKeepsLegacyAllPageSentinel() {
        IPage<Integer> page = PageUtil.page(Arrays.asList(1, 2, 3), 1, -1);

        Assert.assertEquals(Arrays.asList(1, 2, 3), page.getRecords());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewPageRejectsZeroPageSize() {
        PageUtil.newPage(Arrays.asList(), 1, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testPositivePageRejectsAllPageSentinel() {
        PageUtil.checkPositivePage(1, -1);
    }

    @Test(expected = ExternalException.class)
    public void testInterceptorRejectsOversizedPage() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/list");
        request.setParameter("page_size", "501");

        new CustomInterceptor().preHandle(request,
                                          new MockHttpServletResponse(),
                                          new Object());
    }

    @Test
    public void testInterceptorKeepsLegacyAllPageSentinel() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/list");
        request.setParameter("page_size", "-1");

        Assert.assertTrue(new CustomInterceptor().preHandle(
                          request, new MockHttpServletResponse(), new Object()));
    }
}
