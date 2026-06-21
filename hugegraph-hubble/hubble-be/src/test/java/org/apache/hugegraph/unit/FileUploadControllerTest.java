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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import org.apache.hugegraph.config.HugeConfig;
import org.apache.hugegraph.controller.load.FileUploadController;
import org.apache.hugegraph.entity.enums.JobStatus;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.exception.ExternalException;
import org.apache.hugegraph.options.HubbleOptions;
import org.apache.hugegraph.testutil.Assert;

public class FileUploadControllerTest {

    @Test
    public void testCheckFileValidAcceptsUppercaseTrimmedFormat()
           throws Exception {
        FileUploadController controller = this.controller(" CSV ", " TXT ");
        MockMultipartFile file = new MockMultipartFile("file", "HLM.TXT",
                                                       "text/plain",
                                                       "name\nmarko".getBytes());
        JobManager job = JobManager.builder()
                                   .id(1)
                                   .jobStatus(JobStatus.UPLOADING)
                                   .build();

        this.checkFileValid(controller, job, file, "HLM.TXT");
    }

    @Test
    public void testCheckFileValidRejectsMissingExtension() throws Exception {
        FileUploadController controller = this.controller("csv", "txt");
        MockMultipartFile file = new MockMultipartFile("file", "HLM",
                                                       "text/plain",
                                                       "name\nmarko".getBytes());
        JobManager job = JobManager.builder()
                                   .id(1)
                                   .jobStatus(JobStatus.UPLOADING)
                                   .build();

        Assert.assertThrows(ExternalException.class, () -> {
            this.checkFileValid(controller, job, file, "HLM");
        });
    }

    @Test
    public void testCheckFileValidRejectsEmptyWhitelist() throws Exception {
        FileUploadController controller = this.controller((String[]) null);
        MockMultipartFile file = new MockMultipartFile("file", "HLM.TXT",
                                                       "text/plain",
                                                       "name\nmarko".getBytes());
        JobManager job = JobManager.builder()
                                   .id(1)
                                   .jobStatus(JobStatus.UPLOADING)
                                   .build();

        Assert.assertThrows(ExternalException.class, () -> {
            this.checkFileValid(controller, job, file, "HLM.TXT");
        });
    }

    private FileUploadController controller(String... formats) throws Exception {
        FileUploadController controller = new FileUploadController();
        HugeConfig config = Mockito.mock(HugeConfig.class);
        Mockito.when(config.get(HubbleOptions.UPLOAD_FILE_FORMAT_LIST))
               .thenReturn(formats == null ? null : Arrays.asList(formats));
        this.setField(controller, "config", config);
        return controller;
    }

    private void checkFileValid(FileUploadController controller, JobManager job,
                                MockMultipartFile file, String fileName)
                                throws Exception {
        Method method = FileUploadController.class.getDeclaredMethod("checkFileValid",
                                                                    String.class,
                                                                    String.class,
                                                                    int.class,
                                                                    JobManager.class,
                                                                    org.springframework.web.multipart.MultipartFile.class,
                                                                    String.class);
        method.setAccessible(true);
        try {
            method.invoke(controller, "DEFAULT", "hugegraph", 1, job, file,
                          fileName);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) {
                throw (Exception) cause;
            }
            throw e;
        }
    }

    private void setField(Object object, String name, Object value) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(object, value);
    }
}
