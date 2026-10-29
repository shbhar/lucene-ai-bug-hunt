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
package org.apache.lucene.util.fst;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IntsRef;

public class TestFSTByte4WideLabelSpan extends LuceneTestCase {

  public void testNodeLabelsSpanningAlmostAllInts() throws Exception {
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE4, PositiveIntOutputs.getSingleton()).build();
    for (int label : new int[] {0, 1, 2, 3, Integer.MAX_VALUE - 1}) {
      compiler.add(new IntsRef(new int[] {label}, 0, 1), 1L);
    }
    try {
      compiler.compile();
    } catch (ArrayIndexOutOfBoundsException e) {
      // Expected: compile() builds the FST; actual: AIOOBE (presence-byte count wraps negative).
      throw new AssertionError("expected compile() to build the FST, but it threw " + e, e);
    }
  }
}
