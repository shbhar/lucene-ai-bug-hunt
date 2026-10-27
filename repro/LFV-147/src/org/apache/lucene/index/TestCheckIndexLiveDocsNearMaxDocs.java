/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.index;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.FixedBitSet;

/**
 * CheckIndex#bitsCardinality (used by CheckIndex.testLiveDocs) counts live docs in batches of 1024
 * bits with an {@code int offset} incremented by 1024 while {@code offset < bits.length()}; for
 * maxDoc greater than 2^31 - 1024 the increment wraps to a negative offset. Package-private method,
 * called from a test in the same package.
 */
public class TestCheckIndexLiveDocsNearMaxDocs extends LuceneTestCase {

  private static void check(int maxDoc) {
    FixedBitSet live = new FixedBitSet(maxDoc);
    live.set(0, maxDoc);
    live.clear(maxDoc - 1);
    assertEquals(maxDoc - 1, CheckIndex.bitsCardinality(live));
  }

  /** Control: 2^31 - 1024 documents. Passes. */
  public void testMaxDocTwoPow31Minus1024() {
    check(Integer.MAX_VALUE - 1023);
  }

  /** IndexWriter.MAX_DOCS documents. Fails on main. */
  public void testMaxDocEqualsMaxDocs() {
    check(IndexWriter.MAX_DOCS);
  }
}
