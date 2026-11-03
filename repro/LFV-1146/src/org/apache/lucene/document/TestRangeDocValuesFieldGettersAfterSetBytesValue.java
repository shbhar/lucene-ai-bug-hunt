/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.document;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestRangeDocValuesFieldGettersAfterSetBytesValue extends LuceneTestCase {

  public void testGettersFollowSetBytesValue() {
    LongRangeDocValuesField field =
        new LongRangeDocValuesField("r", new long[] {1}, new long[] {2});
    byte[] encoded = new byte[2 * Long.BYTES];
    LongRange.verifyAndEncode(new long[] {10}, new long[] {20}, encoded);
    field.setBytesValue(new BytesRef(encoded));
    // expected: getMin/getMax report the new range [10, 20]; actual: they still return 1 and 2
    assertEquals(10, field.getMin(0));
    assertEquals(20, field.getMax(0));
  }
}
