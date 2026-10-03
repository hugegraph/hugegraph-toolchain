/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with this
 * work for additional information regarding copyright ownership. The ASF
 * licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the
 * License. You may obtain a copy of the License at
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

import java.nio.file.Files;
import java.nio.file.Path;

import javax.sql.DataSource;

import org.apache.hugegraph.config.H2DataSourceConfig;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.sql.init.SqlInitializationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.ResourcePropertySource;

public class H2StartupTest {

    private ApplicationContextRunner runner(String url) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        DataSourceAutoConfiguration.class,
                        SqlInitializationAutoConfiguration.class,
                        MybatisPlusAutoConfiguration.class))
                .withUserConfiguration(H2DataSourceConfig.class)
                .withInitializer(context -> {
                    try {
                        context.getEnvironment().getPropertySources().addLast(
                                new ResourcePropertySource(new FileSystemResource(
                                        "src/main/resources/application.properties")));
                    } catch (java.io.IOException e) {
                        throw new IllegalStateException(e);
                    }
                })
                .withPropertyValues("spring.datasource.url=" + url,
                                    "spring.datasource.username=sa",
                                    "spring.datasource.password=",
                                    "spring.sql.init.mode=always",
                                    "spring.sql.init.schema-locations=" +
                                    "classpath:database/schema.sql");
    }

    @Test
    public void testStartupAndRestartPreserveMetadata() throws Exception {
        Path directory = Files.createTempDirectory("hubble-startup-");
        ApplicationContextRunner runner = this.runner(
                "jdbc:h2:file:" + directory.resolve("metadata"));
        try {
            runner.run(context -> {
                Assert.assertNull(context.getStartupFailure());
                JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                jdbc.update("INSERT INTO user_info(username, locale) VALUES (?, ?)",
                            "test-user", "en");
            });
            runner.run(context -> {
                Assert.assertNull(context.getStartupFailure());
                JdbcTemplate jdbc = new JdbcTemplate(context.getBean(DataSource.class));
                Assert.assertEquals("en", jdbc.queryForObject(
                        "SELECT locale FROM user_info WHERE username = ?",
                        String.class, "test-user"));
                jdbc.update("UPDATE user_info SET locale = ? WHERE username = ?",
                            "zh", "test-user");
                Assert.assertEquals("zh", jdbc.queryForObject(
                        "SELECT locale FROM user_info WHERE username = ?",
                        String.class, "test-user"));
            });
        } finally {
            Files.deleteIfExists(directory.resolve("metadata.mv.db"));
            Files.deleteIfExists(directory.resolve("metadata.trace.db"));
            Files.delete(directory);
        }
    }

    @Test
    public void testProductionMybatisSettingsAreApplied() {
        this.runner("jdbc:h2:mem:mybatis-settings").run(context -> {
            Assert.assertNull(context.getStartupFailure());
            org.apache.ibatis.session.Configuration configuration =
                    context.getBean(SqlSessionFactory.class).getConfiguration();
            Assert.assertFalse(configuration.isCacheEnabled());
            Assert.assertTrue(configuration.isMapUnderscoreToCamelCase());
            Assert.assertTrue(configuration.isUseGeneratedKeys());
            Assert.assertEquals(ExecutorType.REUSE, configuration.getDefaultExecutorType());
            Assert.assertEquals(Integer.valueOf(600), configuration.getDefaultStatementTimeout());
        });
    }

    @Test
    public void testNonH2MetadataIsRejected() {
        this.runner("jdbc:mysql://localhost/hubble").run(context -> {
            Throwable failure = context.getStartupFailure();
            Assert.assertNotNull(failure);
            while (failure.getCause() != null) {
                failure = failure.getCause();
            }
            Assert.assertTrue(failure.getMessage().contains("Hubble metadata requires H2"));
        });
    }
}
