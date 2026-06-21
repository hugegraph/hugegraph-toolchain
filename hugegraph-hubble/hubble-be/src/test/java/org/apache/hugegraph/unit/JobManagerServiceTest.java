/*
 *
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

package org.apache.hugegraph.unit;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.hugegraph.entity.enums.JobStatus;
import org.apache.hugegraph.entity.enums.LoadStatus;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.entity.load.LoadTask;
import org.apache.hugegraph.mapper.load.JobManagerMapper;
import org.apache.hugegraph.service.load.JobManagerService;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.apache.hugegraph.testutil.Assert;
import org.junit.Test;
import org.mockito.Mockito;

public class JobManagerServiceTest {

    @Test
    public void testGetRefreshesLoadingJobToSuccess() throws Exception {
        JobManagerService service = this.serviceWithTasks(this.task(LoadStatus.SUCCEED));

        JobManager job = service.get(1);

        Assert.assertEquals(JobStatus.SUCCESS, job.getJobStatus());
    }

    @Test
    public void testGetRefreshesLoadingJobToFailed() throws Exception {
        JobManagerService service = this.serviceWithTasks(this.task(LoadStatus.FAILED));

        JobManager job = service.get(1);

        Assert.assertEquals(JobStatus.FAILED, job.getJobStatus());
    }

    @Test
    public void testGetKeepsLoadingJobWithRunningTask() throws Exception {
        JobManagerService service = this.serviceWithTasks(this.task(LoadStatus.RUNNING));

        JobManager job = service.get(1);

        Assert.assertEquals(JobStatus.LOADING, job.getJobStatus());
    }

    @Test
    public void testGetKeepsLoadingJobWithEmptyTaskList() throws Exception {
        JobManagerService service = this.serviceWithTasks();

        JobManager job = service.get(1);

        Assert.assertEquals(JobStatus.LOADING, job.getJobStatus());
    }

    @Test
    public void testListByIdsRefreshesLoadingJobs() throws Exception {
        JobManagerService service = this.serviceWithTasks(this.task(LoadStatus.SUCCEED));

        List<JobManager> jobs = service.list(1, Collections.singletonList(1));

        Assert.assertEquals(JobStatus.SUCCESS, jobs.get(0).getJobStatus());
    }

    private JobManagerService serviceWithTasks(LoadTask... tasks) throws Exception {
        JobManagerService service = new JobManagerService();
        JobManager job = JobManager.builder()
                                   .id(1)
                                   .connId(1)
                                   .jobStatus(JobStatus.LOADING)
                                   .createTime(new Date())
                                   .updateTime(new Date())
                                   .build();
        JobManagerMapper mapper = Mockito.mock(JobManagerMapper.class);
        Mockito.when(mapper.selectById(1)).thenReturn(job);
        Mockito.when(mapper.selectBatchIds(Collections.singletonList(1)))
               .thenReturn(Collections.singletonList(job));
        Mockito.when(mapper.updateById(Mockito.any(JobManager.class))).thenReturn(1);

        LoadTaskService taskService = Mockito.mock(LoadTaskService.class);
        Mockito.when(taskService.taskListByJob(1)).thenReturn(Arrays.asList(tasks));

        this.setField(service, "mapper", mapper);
        this.setField(service, "taskService", taskService);
        return service;
    }

    private LoadTask task(LoadStatus status) {
        return LoadTask.builder().status(status).build();
    }

    private void setField(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
