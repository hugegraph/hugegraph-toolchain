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

package org.apache.hugegraph.loader.test.unit;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;

import org.apache.hugegraph.loader.util.ParquetUtil;
import org.apache.hugegraph.testutil.Assert;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.SimpleGroupFactory;
import org.apache.parquet.io.api.Binary;
import org.apache.parquet.schema.MessageType;
import org.apache.parquet.schema.MessageTypeParser;
import org.junit.Test;

public class ParquetUtilTest {

    private static final long JULIAN_DAY_AT_UNIX_EPOCH = 2440588L;

    @Test
    public void testInt96Midnight() {
        assertTimestamp(LocalDate.of(2019, 12, 9), LocalTime.MIDNIGHT);
    }

    @Test
    public void testInt96FractionalSecondsAcrossDayBoundary() {
        assertTimestamp(LocalDate.of(2020, 2, 29),
                        LocalTime.of(23, 59, 59, 123456789));
    }

    @Test
    public void testInt96BeforeUnixEpoch() {
        assertTimestamp(LocalDate.of(1969, 12, 31), LocalTime.MIDNIGHT);
    }

    private static void assertTimestamp(LocalDate day, LocalTime time) {
        MessageType schema = MessageTypeParser.parseMessageType(
                "message timestamp { required int96 value; }");
        Group group = new SimpleGroupFactory(schema).newGroup();
        // INT96 stores nanoseconds since midnight followed by the Julian day.
        int julianDay = Math.toIntExact(day.toEpochDay() +
                                       JULIAN_DAY_AT_UNIX_EPOCH);
        byte[] encoded = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN)
                                   .putLong(time.toNanoOfDay())
                                   .putInt(julianDay).array();
        group.add("value", Binary.fromConstantByteArray(encoded));

        Object actual = ParquetUtil.convertObject(group, 0);
        Assert.assertTrue(actual instanceof Date);
        long expected = day.atTime(time).atZone(ZoneId.systemDefault())
                           .toInstant().toEpochMilli();
        Assert.assertEquals(expected, ((Date) actual).getTime());
    }
}
