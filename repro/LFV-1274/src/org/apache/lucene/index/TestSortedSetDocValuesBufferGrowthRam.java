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
package org.apache.lucene.index;

import java.util.Collections;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.RamUsageEstimator;

public class TestSortedSetDocValuesBufferGrowthRam extends LuceneTestCase {

  public void testBufferGrowthChargedOnce() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMaxBufferedDocs(10))) {
      SortedSetDocValuesField a = new SortedSetDocValuesField("f", new BytesRef("a"));
      w.addDocument(Collections.nCopies(8, a));
      long before = w.ramBytesUsed();
      w.addDocument(Collections.nCopies(9, a));
      long growth =
          RamUsageEstimator.sizeOf(new int[ArrayUtil.oversize(9, Integer.BYTES)])
              - RamUsageEstimator.sizeOf(new int[8]);
      // expected: RAM grows once by the int[8] -> int[12] buffer growth (16 bytes); actual: twice (32)
      assertEquals(growth, w.ramBytesUsed() - before);
    }
  }
}
