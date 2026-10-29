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
package org.apache.lucene.util;

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRoaringDocIdSetLastBlock extends LuceneTestCase {

  public void testAddRangeInLastBlock() {
    int maxDoc = IndexWriter.MAX_DOCS;
    RoaringDocIdSet.Builder builder = new RoaringDocIdSet.Builder(maxDoc);
    // expected: adds the 10 docs; actual: throws, the block end (block + 1) << 16 overflows
    try {
      builder.add(maxDoc - 10, maxDoc);
    } catch (Throwable t) {
      throw new AssertionError("expected add(maxDoc - 10, maxDoc) to add 10 docs, got " + t, t);
    }
    assertEquals(10, builder.build().cardinality());
  }
}
