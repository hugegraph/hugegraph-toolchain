/*
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

package org.apache.hugegraph.controller.ingest;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.extern.log4j.Log4j2;
import org.apache.hugegraph.common.Constant;
import org.apache.hugegraph.common.Response;
import org.apache.hugegraph.entity.enums.JobStatus;
import org.apache.hugegraph.entity.enums.LoadStatus;
import org.apache.hugegraph.entity.load.Datasource;
import org.apache.hugegraph.entity.load.JobManager;
import org.apache.hugegraph.entity.load.LoadTask;
import org.apache.hugegraph.service.load.DatasourceService;
import org.apache.hugegraph.service.load.JobManagerService;
import org.apache.hugegraph.service.load.LoadTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Log4j2
@RestController
@RequestMapping(Constant.API_VERSION + "ingest")
public class IngestController {

    @Autowired
    private JobManagerService jobManagerService;
    @Autowired
    private LoadTaskService loadTaskService;
    @Autowired
    private DatasourceService datasourceService;

    // ===== Datasource endpoints =====

    @GetMapping("/datasources/list")
    public Response datasourceList(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "page_no", required = false, defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", required = false, defaultValue = "10") int pageSize) {
        IPage<Datasource> page = datasourceService.list(pageNo, pageSize, query);
        return Response.builder().status(Constant.STATUS_OK).data(page).build();
    }

    @GetMapping("/datasources/{id}")
    public Response datasourceGet(@PathVariable("id") int id) {
        Datasource ds = datasourceService.get(id);
        if (ds == null) {
            return Response.builder().status(Constant.STATUS_NOT_FOUND)
                           .message("Datasource not found: " + id).build();
        }
        return Response.builder().status(Constant.STATUS_OK).data(ds).build();
    }

    @PostMapping("/datasources")
    public Response datasourceCreate(@RequestBody Datasource entity) {
        datasourceService.save(entity);
        return Response.builder().status(Constant.STATUS_OK)
                       .data(Map.of("datasource_id", entity.getId())).build();
    }

    @DeleteMapping("/datasources/{id}")
    public Response datasourceDelete(@PathVariable("id") int id) {
        datasourceService.remove(id);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    @PostMapping("/datasources/delete")
    public Response datasourceBatchDelete(@RequestBody List<Integer> ids) {
        datasourceService.removeBatch(ids);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    // ===== Task endpoints (job_manager table) =====

    @GetMapping("/tasks/list")
    public Response taskList(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "page_no", required = false, defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", required = false, defaultValue = "10") int pageSize) {

        // list all jobs across all graphspaces - use empty strings to get all
        // We need to query without graphspace/graph filter for the ingest view
        IPage<JobManager> page = jobManagerService.listAll(pageNo, pageSize, query);

        IPage<TaskVO> result = page.convert(job -> {
            TaskVO vo = new TaskVO();
            vo.taskId = job.getId();
            vo.taskName = job.getJobName();
            vo.taskScheduleType = "ONCE";
            vo.taskScheduleStatus = toScheduleStatus(job.getJobStatus());
            vo.createTime = job.getCreateTime();
            vo.creator = "";

            // Build ingestion_option from job fields
            Map<String, Object> option = new HashMap<>();
            option.put("graphspace", job.getGraphSpace());
            option.put("graph", job.getGraph());
            vo.ingestionOption = option;

            // Build ingestion_mapping with structs from file mappings
            List<LoadTask> tasks = loadTaskService.taskListByJob(job.getId());
            List<Map<String, Object>> structs = tasks.stream().map(t -> {
                Map<String, Object> struct = new HashMap<>();
                Map<String, Object> input = new HashMap<>();
                input.put("type", "FILE");
                input.put("path", t.getFileName());
                struct.put("input", input);
                return struct;
            }).collect(Collectors.toList());
            Map<String, Object> mapping = new HashMap<>();
            mapping.put("structs", structs);
            vo.ingestionMapping = mapping;

            // Build last_metrics from latest load task
            if (!tasks.isEmpty()) {
                LoadTask latest = tasks.get(tasks.size() - 1);
                Map<String, Object> metrics = new HashMap<>();
                metrics.put("status", latest.getStatus().name());
                metrics.put("load_progress", latest.getLoadProgress());
                vo.lastMetrics = metrics;
            }

            // Build job_summary
            JobSummaryVO summary = new JobSummaryVO();
            for (LoadTask t : tasks) {
                if (t.getStatus() == LoadStatus.SUCCEED) summary.successCount++;
                else if (t.getStatus() == LoadStatus.FAILED) summary.failedCount++;
                else if (t.getStatus().inRunning()) summary.runningCount++;
            }
            vo.jobSummary = summary;

            return vo;
        });

        return Response.builder().status(Constant.STATUS_OK).data(result).build();
    }

    @GetMapping("/tasks/{id}")
    public Response taskDetail(@PathVariable("id") int id) {
        JobManager job = jobManagerService.get(id);
        if (job == null) {
            return Response.builder().status(Constant.STATUS_NOT_FOUND)
                           .message("Task not found: " + id).build();
        }
        return Response.builder().status(Constant.STATUS_OK).data(job).build();
    }

    @DeleteMapping("/tasks/{id}")
    public Response deleteTask(@PathVariable("id") int id) {
        jobManagerService.remove(id);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    @PutMapping("/tasks/{id}/enable")
    public Response enableTask(@PathVariable("id") int id) {
        JobManager job = jobManagerService.get(id);
        if (job == null) {
            return Response.builder().status(Constant.STATUS_NOT_FOUND)
                           .message("Task not found: " + id).build();
        }
        job.setJobStatus(JobStatus.DEFAULT);
        jobManagerService.update(job);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    @PutMapping("/tasks/{id}/disable")
    public Response disableTask(@PathVariable("id") int id) {
        JobManager job = jobManagerService.get(id);
        if (job == null) {
            return Response.builder().status(Constant.STATUS_NOT_FOUND)
                           .message("Task not found: " + id).build();
        }
        job.setJobStatus(JobStatus.FAILED);
        jobManagerService.update(job);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    // ===== Job endpoints (load_task table) =====

    @GetMapping("/jobs/list")
    public Response jobList(
            @RequestParam(name = "taskid", required = false, defaultValue = "0") int taskId,
            @RequestParam(name = "page_no", required = false, defaultValue = "1") int pageNo,
            @RequestParam(name = "page_size", required = false, defaultValue = "10") int pageSize) {

        List<LoadTask> tasks = loadTaskService.taskListByJob(taskId);

        // Manual pagination
        int total = tasks.size();
        int fromIndex = Math.min((pageNo - 1) * pageSize, total);
        int toIndex = Math.min(fromIndex + pageSize, total);
        List<LoadTask> pageData = tasks.subList(fromIndex, toIndex);

        List<JobVO> records = pageData.stream().map(t -> {
            JobVO vo = new JobVO();
            vo.jobId = t.getId();
            vo.taskId = t.getJobId();
            vo.jobStatus = t.getStatus().name();
            vo.jobMessage = "";
            vo.createTime = t.getCreateTime();

            JobMetricsVO metrics = new JobMetricsVO();
            metrics.totalCount = t.getFileReadLines() != null ? t.getFileReadLines() : 0L;
            long durationMs = t.getDuration() != null ? t.getDuration() : 0L;
            long durationSec = durationMs > 0 ? durationMs / 1000 : 1;
            metrics.avgRate = durationMs > 0 ? (float) metrics.totalCount / durationSec : 0f;
            metrics.curRate = t.getStatus().inRunning() ? metrics.avgRate : 0f;
            metrics.totalTime = durationMs;
            vo.jobMetrics = metrics;

            return vo;
        }).collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("size", pageSize);
        result.put("current", pageNo);

        return Response.builder().status(Constant.STATUS_OK).data(result).build();
    }

    @GetMapping("/jobs/{id}")
    public Response jobDetail(@PathVariable("id") int id) {
        LoadTask task = loadTaskService.get(id);
        if (task == null) {
            return Response.builder().status(Constant.STATUS_NOT_FOUND)
                           .message("Job not found: " + id).build();
        }
        return Response.builder().status(Constant.STATUS_OK).data(task).build();
    }

    @DeleteMapping("/jobs/{id}")
    public Response deleteJob(@PathVariable("id") int id) {
        loadTaskService.remove(id);
        return Response.builder().status(Constant.STATUS_OK).build();
    }

    // ===== Metrics =====

    @GetMapping("/metrics/task")
    public Response metricsTask() {
        List<JobManager> all = jobManagerService.listAll();

        long runningOnce = 0, runningCron = 0, runningRealtime = 0;
        long todoOnce = 0, todoCron = 0, todoRealtime = 0;

        for (JobManager job : all) {
            if (job.getJobStatus() == JobStatus.LOADING) {
                runningOnce++;
            } else if (job.getJobStatus() == JobStatus.DEFAULT ||
                       job.getJobStatus() == JobStatus.SETTING) {
                todoOnce++;
            }
        }

        Map<String, Object> todo = new HashMap<>();
        todo.put("ONCE", todoOnce);
        todo.put("CRON", todoCron);
        todo.put("REALTIME", todoRealtime);

        Map<String, Object> running = new HashMap<>();
        running.put("ONCE", runningOnce);
        running.put("CRON", runningCron);
        running.put("REALTIME", runningRealtime);

        Map<String, Object> data = new HashMap<>();
        data.put("total_realtime_size", 0);
        data.put("total_other_size", all.size());
        data.put("todo", todo);
        data.put("running", running);

        return Response.builder().status(Constant.STATUS_OK).data(data).build();
    }

    // ===== Helpers =====

    private String toScheduleStatus(JobStatus status) {
        if (status == JobStatus.FAILED) return "DISABLE";
        return "ENABLE";
    }

    // ===== VOs =====

    @Data
    static class TaskVO {
        @JsonProperty("task_id") Integer taskId;
        @JsonProperty("task_name") String taskName;
        @JsonProperty("task_schedule_type") String taskScheduleType;
        @JsonProperty("task_schedule_status") String taskScheduleStatus;
        @JsonProperty("ingestion_option") Object ingestionOption;
        @JsonProperty("ingestion_mapping") Object ingestionMapping;
        @JsonProperty("last_metrics") Object lastMetrics;
        @JsonProperty("job_summary") JobSummaryVO jobSummary;
        @JsonProperty("create_time") Date createTime;
        @JsonProperty("creator") String creator;
    }

    @Data
    static class JobSummaryVO {
        @JsonProperty("success_count") int successCount;
        @JsonProperty("failed_count") int failedCount;
        @JsonProperty("running_count") int runningCount;
    }

    @Data
    static class JobVO {
        @JsonProperty("job_id") Integer jobId;
        @JsonProperty("task_id") Integer taskId;
        @JsonProperty("job_status") String jobStatus;
        @JsonProperty("job_message") String jobMessage;
        @JsonProperty("job_metrics") JobMetricsVO jobMetrics;
        @JsonProperty("create_time") Date createTime;
    }

    @Data
    static class JobMetricsVO {
        @JsonProperty("total_count") long totalCount;
        @JsonProperty("avg_rate") float avgRate;
        @JsonProperty("cur_rate") float curRate;
        @JsonProperty("total_time") long totalTime;
    }
}
