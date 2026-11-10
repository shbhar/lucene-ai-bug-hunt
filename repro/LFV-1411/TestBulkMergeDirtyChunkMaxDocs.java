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
package org.apache.lucene.codecs.lucene90.compressing;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.codecs.compressing.FastCompressingCodec;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBulkMergeDirtyChunkMaxDocs extends LuceneTestCase {

  public void testBulkMergeDirtyChunkFromLargerMaxDocsPerChunk() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig first =
          new IndexWriterConfig()
              .setCodec(new FastCompressingCodec(1 << 14, 2, false, 10))
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, first)) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new StoredField("id", i));
          w.addDocument(doc);
          w.commit();
        }
      }
      IndexWriterConfig second =
          new IndexWriterConfig()
              .setCodec(new FastCompressingCodec(1 << 14, 1, false, 10))
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(dir, second)) {
        // Expected: the bulk merge of the two 1-doc dirty chunks succeeds; actual (-ea):
        // AssertionError in copyChunks.
        w.forceMerge(1);
      }
    }
  }
}
