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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexConcurrentMissingFile extends LuceneTestCase {

  // Expected: 1 bad segment (as with one thread); actual: IllegalArgumentException from the sort.
  public void testConcurrentCheckWithMissingFile() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setUseCompoundFile(false)
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int segment = 0; segment < 32; segment++) {
          for (int i = 0; i <= segment % 16; i++) {
            Document doc = new Document();
            doc.add(new StoredField("f", "document " + i));
            w.addDocument(doc);
          }
          w.commit();
        }
      }
      dir.deleteFile(SegmentInfos.readLatestCommit(dir).info(20).info.name + ".fdt");
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(2);
        assertEquals(1, checker.checkIndex().numBadSegments);
      }
    }
  }
}
