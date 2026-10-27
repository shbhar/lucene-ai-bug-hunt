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

/**
 * MappingCharFilter overrides reset() to rewind the input and re-read it (upstream
 * TestMappingCharFilter#testReaderReset), but the offset corrections recorded by BaseCharFilter
 * during the first pass are not cleared. The second pass appends new corrections behind the old
 * ones, computed from the old cumulative diff: correctOffset() is wrong after reset() (for other
 * inputs, with -ea, BaseCharFilter's ordering assertion fires instead). Public API only.
 */
public class TestMappingCharFilterResetOffsets extends LuceneTestCase {

  private static String readAll(MappingCharFilter f) throws Exception {
    StringBuilder sb = new StringBuilder();
    int c;
    while ((c = f.read()) != -1) {
      sb.append((char) c);
    }
    return sb.toString();
  }

  public void testOffsetsAfterReset() throws Exception {
    NormalizeCharMap.Builder b = new NormalizeCharMap.Builder();
    b.add("a", "xy");
    MappingCharFilter f = new MappingCharFilter(b.build(), new StringReader("a"));

    assertEquals("xy", readAll(f));
    assertEquals(0, f.correctOffset(0));
    assertEquals(0, f.correctOffset(1));
    assertEquals(1, f.correctOffset(2)); // final offset == input length

    f.reset(); // rewinds the underlying StringReader, as in TestMappingCharFilter#testReaderReset

    assertEquals("xy", readAll(f));
    assertEquals(0, f.correctOffset(0));
    assertEquals(0, f.correctOffset(1));
    assertEquals(1, f.correctOffset(2));
  }
}
