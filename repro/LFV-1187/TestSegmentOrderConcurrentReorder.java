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

import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentOrder;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.NamedThreadFactory;

public class TestSegmentOrderConcurrentReorder extends LuceneTestCase {

  // Two concurrent reorder() calls on one shared SegmentOrder should both succeed.
  public void testConcurrentReorderOnSharedSegmentOrder() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new LongPoint("f", i));
          w.addDocument(doc);
          w.flush();
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        SegmentOrder order =
            SegmentOrder.fromSort(new Sort(new SortField("f", SortField.Type.LONG, true)));
        CyclicBarrier barrier = new CyclicBarrier(2);
        Callable<IndexReader> reorder =
            () -> {
              barrier.await();
              return order.reorder(reader);
            };
        ExecutorService executor =
            Executors.newFixedThreadPool(2, new NamedThreadFactory("reorder"));
        try {
          Future<IndexReader> a = executor.submit(reorder);
          Future<IndexReader> b = executor.submit(reorder);
          a.get();
          b.get();
        } finally {
          TestUtil.shutdownExecutorService(executor);
        }
      }
    }
  }
}
