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

// Preserve the complete historical examples from GremlinUtil, including their
// labels and ID strategies, without creating templates on the Server automatically.
const LEGACY_SCHEMA_TEMPLATES = {
    hlm: [
        "graph.schema().propertyKey('姓名').asText().ifNotExist().create();",
        "graph.schema().propertyKey('性别').asText().ifNotExist().create();",
        "graph.schema().propertyKey('年龄').asInt().ifNotExist().create();",
        "graph.schema().propertyKey('特点').asText().ifNotExist().create();",
        "graph.schema().propertyKey('亲疏').asText().ifNotExist().create();",
        "",
        "graph.schema().vertexLabel('男人')",
        "    .properties('姓名', '性别', '年龄', '特点', '亲疏')",
        "    .primaryKeys('姓名').ifNotExist().create();",
        "graph.schema().vertexLabel('女人')",
        "    .properties('姓名', '性别', '年龄', '特点', '亲疏')",
        "    .primaryKeys('姓名').ifNotExist().create();",
        "graph.schema().vertexLabel('机构')",
        "    .properties('姓名', '特点', '亲疏')",
        "    .primaryKeys('姓名').ifNotExist().create();",
        "",
        "graph.schema().edgeLabel('父子')",
        "    .sourceLabel('男人').targetLabel('男人').ifNotExist().create();",
        "graph.schema().edgeLabel('父女')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('母子')",
        "    .sourceLabel('女人').targetLabel('男人').ifNotExist().create();",
        "graph.schema().edgeLabel('母女')",
        "    .sourceLabel('女人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('妻')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('妾')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('相恋')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('朋友')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('姐妹')",
        "    .sourceLabel('女人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('丫环')",
        "    .sourceLabel('男人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('丫头')",
        "    .sourceLabel('女人').targetLabel('女人').ifNotExist().create();",
        "graph.schema().edgeLabel('兵部指挥')",
        "    .sourceLabel('机构').targetLabel('男人').ifNotExist().create();",
        "graph.schema().edgeLabel('巡盐御史')",
        "    .sourceLabel('机构').targetLabel('男人').ifNotExist().create();",
        "graph.schema().edgeLabel('尚书令')",
        "    .sourceLabel('机构').targetLabel('男人').ifNotExist().create();",
    ].join('\n'),
    covid19: [
        "graph.schema().propertyKey('性别').asText().ifNotExist().create();",
        "graph.schema().propertyKey('年龄').asInt().ifNotExist().create();",
        "graph.schema().propertyKey('防疫政策').asText().ifNotExist().create();",
        "graph.schema().propertyKey('类型').asText().ifNotExist().create();",
        "graph.schema().propertyKey('时间').asText().ifNotExist().create();",
        "",
        "graph.schema().vertexLabel('patient')",
        "    .properties('性别','年龄')",
        "    .useCustomizeStringId().nullableKeys('性别','年龄')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().vertexLabel('place')",
        "    .useCustomizeStringId()",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().vertexLabel('city')",
        "    .properties('防疫政策')",
        "    .useCustomizeStringId().nullableKeys('防疫政策')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().vertexLabel('vehicle')",
        "    .properties('类型')",
        "    .useCustomizeStringId().nullableKeys('类型')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "",
        "graph.schema().edgeLabel('relation')",
        "    .sourceLabel('patient').targetLabel('patient')",
        "    .properties('类型').nullableKeys('类型')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().edgeLabel('reside')",
        "    .sourceLabel('patient').targetLabel('place')",
        "    .properties('时间').nullableKeys('时间')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().edgeLabel('work')",
        "    .sourceLabel('patient').targetLabel('place')",
        "    .properties('时间').nullableKeys('时间')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().edgeLabel('stay')",
        "    .sourceLabel('patient').targetLabel('place')",
        "    .properties('时间').nullableKeys('时间')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().edgeLabel('comfirm')",
        "    .sourceLabel('patient').targetLabel('city')",
        "    .properties('时间').nullableKeys('时间')",
        "    .enableLabelIndex(false).ifNotExist().create();",
        "graph.schema().edgeLabel('take')",
        "    .sourceLabel('patient').targetLabel('vehicle')",
        "    .properties('时间').nullableKeys('时间')",
        "    .enableLabelIndex(false).ifNotExist().create();",
    ].join('\n'),
};

export default LEGACY_SCHEMA_TEMPLATES;
