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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Phaser;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndexSearcherCountPartitionHang extends LuceneTestCase {

  public void testCountRethrowsTimeoutFromPartitionedSegment() throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(2);
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 2; i++) {
          Document doc = new Document();
          doc.add(new StringField("f", "a", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      Phaser bothPartitionsStarted = new Phaser(2);
      try (DirectoryReader reader =
          new ExitableDirectoryReader(DirectoryReader.open(dir), () -> true)) {
        IndexSearcher searcher =
            new IndexSearcher(reader, pool) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                return slices(leaves, 1, 1, true);
              }

              @Override
              protected void searchLeaf(
                  LeafReaderContext ctx,
                  int minDocId,
                  int maxDocId,
                  Weight weight,
                  Collector collector)
                  throws IOException {
                bothPartitionsStarted.arriveAndAwaitAdvance();
                super.searchLeaf(ctx, minDocId, maxDocId, weight, collector);
              }
            };
        Future<Integer> count =
            pool.submit(() -> searcher.count(new TermQuery(new Term("f", "a"))));
        // expected: count() rethrows the timeout thrown by Weight#count; actual: it never returns
        expectThrows(ExecutionException.class, () -> count.get(10, TimeUnit.SECONDS));
      }
    } finally {
      pool.shutdownNow();
    }
  }
}
