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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.IOSupplier;

public class TestPooledReaderReleaseFailure extends LuceneTestCase {

  public void testNrtOpenAfterThrowingClosedListener() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(
        new FilterMergePolicy(new TieredMergePolicy()) {
          @Override
          public int numDeletesToMerge(
              SegmentCommitInfo info, int delCount, IOSupplier<CodecReader> readerSupplier)
              throws IOException {
            readerSupplier.get();
            return super.numDeletesToMerge(info, delCount, readerSupplier);
          }
        });
    try (IndexWriter w = new IndexWriter(new ByteBuffersDirectory(), iwc)) {
      for (String id : new String[] {"a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("id", id, Field.Store.NO));
        w.addDocument(doc);
      }
      DirectoryReader r = DirectoryReader.open(w);
      r.leaves()
          .get(0)
          .reader()
          .getReaderCacheHelper()
          .addClosedListener(
              _ -> {
                throw new IOException("listener");
              });
      r.close();
      w.deleteDocuments(new Term("id", "a"));
      expectThrows(IOException.class, () -> DirectoryReader.open(w));
      // Expected: numDocs 1. Actual: AlreadyClosedException, the pool kept the closed reader.
      try (DirectoryReader r2 = DirectoryReader.open(w)) {
        assertEquals(1, r2.numDocs());
      }
    }
  }
}
