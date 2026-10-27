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

import java.util.Random;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Several threads update doc values; each thread owns a disjoint set of documents, so the updates
 * of one document are issued in a known order. Each update goes either through
 * updateDocValues(Term) or through tryUpdateDocValue on a (possibly stale) NRT reader, falling back
 * to updateDocValues(Term) when it returns -1. Expected: every document ends with the last value
 * written to it. NoMergePolicy keeps merges (and their own doc-values carry-over logic) out of the
 * picture.
 */
public class TestTryUpdateDocValueOrderStress extends LuceneTestCase {

  public void testLastWriteWinsPerDocument() throws Exception {
    final int numDocs = 200, numThreads = 4, opsPerThread = 500;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (int i = 0; i < numDocs; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new NumericDocValuesField("n", 0));
        w.addDocument(d);
      }
      final long[] expected = new long[numDocs];
      final AtomicLong nextValue = new AtomicLong(1);
      final AtomicLong tryOk = new AtomicLong(), tryDeclined = new AtomicLong();
      try (SearcherManager mgr = new SearcherManager(w, null)) {
        CyclicBarrier start = new CyclicBarrier(numThreads);
        Thread[] threads = new Thread[numThreads];
        final long seed = random().nextLong();
        for (int t = 0; t < numThreads; t++) {
          final int owner = t;
          threads[t] =
              new Thread(
                  () -> {
                    Random rnd = new Random(seed + owner);
                    try {
                      start.await();
                      for (int op = 0; op < opsPerThread; op++) {
                        int id = owner + numThreads * rnd.nextInt(numDocs / numThreads);
                        long value = nextValue.getAndIncrement();
                        Term term = new Term("id", Integer.toString(id));
                        if (rnd.nextBoolean()) {
                          w.updateDocValues(term, new NumericDocValuesField("n", value));
                        } else {
                          IndexSearcher s = mgr.acquire();
                          try {
                            TopDocs td = s.search(new TermQuery(term), 1);
                            long seq =
                                w.tryUpdateDocValue(
                                    s.getIndexReader(),
                                    td.scoreDocs[0].doc,
                                    new NumericDocValuesField("n", value));
                            if (seq == -1) {
                              tryDeclined.incrementAndGet();
                              w.updateDocValues(term, new NumericDocValuesField("n", value));
                            } else {
                              tryOk.incrementAndGet();
                            }
                          } finally {
                            mgr.release(s);
                          }
                        }
                        expected[id] = value; // only this thread writes this slot
                        int r = rnd.nextInt(20);
                        if (r == 0) {
                          mgr.maybeRefresh();
                        } else if (r == 1) {
                          Document d = new Document();
                          d.add(new StringField("id", "filler", Field.Store.NO));
                          w.addDocument(d);
                        }
                      }
                    } catch (Exception e) {
                      throw new AssertionError(e);
                    }
                  });
          threads[t].start();
        }
        for (Thread t : threads) {
          t.join();
        }
      }
      int wrong = 0;
      String firstWrong = null;
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        for (int id = 0; id < numDocs; id++) {
          TopDocs td = s.search(new TermQuery(new Term("id", Integer.toString(id))), 1);
          int doc = td.scoreDocs[0].doc;
          LeafReaderContext leaf = r.leaves().get(ReaderUtil.subIndex(doc, r.leaves()));
          NumericDocValues n = leaf.reader().getNumericDocValues("n");
          assertTrue(n.advanceExact(doc - leaf.docBase));
          if (n.longValue() != expected[id]) {
            wrong++;
            if (firstWrong == null) {
              firstWrong = "id=" + id + " expected n=" + expected[id] + " got n=" + n.longValue();
            }
          }
        }
      }
      System.out.println(
          "try ok=" + tryOk + ", try returned -1=" + tryDeclined + ", wrong documents=" + wrong);
      assertEquals("documents whose last write was lost (first: " + firstWrong + ")", 0, wrong);
    }
  }
}
