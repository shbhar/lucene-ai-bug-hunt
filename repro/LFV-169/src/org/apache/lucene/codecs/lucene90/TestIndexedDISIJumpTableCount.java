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
package org.apache.lucene.codecs.lucene90;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexedDISIJumpTableCount extends LuceneTestCase {

  public void testJumpTableEntryCount() throws Exception {
    int doc = 32766 << 16; // 2,147,352,576 < IndexWriter.MAX_DOCS
    try (Directory dir = new ByteBuffersDirectory();
        IndexOutput out = dir.createOutput("disi", IOContext.DEFAULT)) {
      assertEquals(
          "expected the 32768 jump-table entries written (blocks 0..32767), actual the short cast",
          32768,
          IndexedDISI.writeBitSet(
              DocIdSetIterator.range(doc, doc + 1), out, IndexedDISI.DEFAULT_DENSE_RANK_POWER));
    }
  }
}
