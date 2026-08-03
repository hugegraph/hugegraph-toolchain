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

package org.apache.hugegraph.mapper.query;

import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.springframework.stereotype.Component;

import org.apache.hugegraph.entity.query.ExecuteHistory;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

@Mapper
@Component
public interface ExecuteHistoryMapper extends BaseMapper<ExecuteHistory> {

    // 删除超出限制的记录, 按 (graphspace, graph) 分别保留最新的 limit 条
    default void deleteExceedLimit(int limit) {
        for (ExecuteHistory scope : findScopes()) {
            List<Long> ids = findIdsNewestFirst(scope.getGraphspace(),
                                                scope.getGraph());
            if (ids.size() > limit) {
                // 在 Java 侧切分而不是用 SQL 的 OFFSET: 带绑定参数的
                // LIMIT/OFFSET 在不同数据库下语义不一致, 会删错行.
                deleteBatchIds(ids.subList(limit, ids.size()));
            }
        }
    }

    // 查询所有出现过的 (graphspace, graph) 组合
    @Select("SELECT `graphspace`, `graph` FROM `execute_history` " +
            "GROUP BY `graphspace`, `graph`")
    List<ExecuteHistory> findScopes();

    // 按时间倒序查询该 graphspace/graph 下的全部 id, 最新的在最前
    @Select("SELECT `id` FROM `execute_history` " +
            "WHERE `graphspace` = #{graphspace} AND `graph` = #{graph} " +
            "ORDER BY `create_time` DESC, `id` DESC")
    List<Long> findIdsNewestFirst(@Param("graphspace") String graphspace,
                                  @Param("graph") String graph);
}
