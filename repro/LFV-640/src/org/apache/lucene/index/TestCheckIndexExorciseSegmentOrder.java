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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexExorciseSegmentOrder extends LuceneTestCase {

  public void testConcurrentExorciseKeepsCommitOrder() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setUseCompoundFile(false)
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int numDocs : new int[] {10, 1, 1}) {
          for (int i = 0; i < numDocs; i++) {
            w.addDocument(List.of(new StoredField("f", i)));
          }
          w.commit();
        }
      }
      int length = (int) dir.fileLength("_1.fdt");
      dir.deleteFile("_1.fdt");
      try (IndexOutput out = dir.createOutput("_1.fdt", IOContext.DEFAULT)) {
        out.writeBytes(new byte[length], length);
      }

      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(2);
        checker.exorciseIndex(checker.checkIndex());
      }

      List<String> segments = new ArrayList<>();
      for (SegmentCommitInfo info : SegmentInfos.readLatestCommit(dir)) {
        segments.add(info.info.name);
      }
      // expected: _1 dropped, _0 and _2 kept in commit order; actual: [_2, _0] (size order)
      assertEquals(List.of("_0", "_2"), segments);
    }
  }
}
