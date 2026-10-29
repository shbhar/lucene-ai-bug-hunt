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
package org.apache.lucene.analysis.charfilter;

import java.io.StringReader;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMappingCharFilterResetOffsets extends LuceneTestCase {

  public void testCorrectOffsetAfterReset() throws Exception {
    NormalizeCharMap.Builder map = new NormalizeCharMap.Builder();
    map.add("a", "xy");
    MappingCharFilter f = new MappingCharFilter(map.build(), new StringReader("a"));
    while (f.read() != -1) {}
    f.reset();
    while (f.read() != -1) {}
    // expected: the end of the re-read output "xy" maps to the end of the input "a" (1); actual: 0
    assertEquals("correctOffset(2) after reset()", 1, f.correctOffset(2));
  }
}
