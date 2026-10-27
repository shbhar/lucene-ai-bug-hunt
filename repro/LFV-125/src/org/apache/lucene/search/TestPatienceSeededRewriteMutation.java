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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * PatienceKnnVectorQuery.rewrite assigns a new SeededKnnVectorQuery, holding a seed Weight built on
 * the calling searcher, to the query's own (non-final) delegate field. Running the query therefore
 * modifies it: equals/hashCode change, and a concurrent search of the same query instance on
 * another reader replaces the seed Weight that an in-flight search is still using.
 */
public class TestPatienceSeededRewriteMutation extends LuceneTestCase {

  private static Directory index(int segments, int perSegment, int offset) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < segments; s++) {
        for (int i = 0; i < perSegment; i++) {
          int x = offset + s * perSegment + i;
          Document d = new Document();
          d.add(new StringField("tag", x % 2 == 0 ? "even" : "odd", Field.Store.NO));
          d.add(new KnnFloatVectorField("v", new float[] {x, 1}, VectorSimilarityFunction.EUCLIDEAN));
          w.addDocument(d);
        }
        w.commit();
      }
    }
    return dir;
  }

  private static PatienceKnnVectorQuery newQuery() {
    KnnFloatVectorQuery knn = new KnnFloatVectorQuery("v", new float[] {3, 1}, 5);
    return PatienceKnnVectorQuery.fromSeededQuery(
        SeededKnnVectorQuery.fromFloatQuery(knn, new TermQuery(new Term("tag", "even"))));
  }

  private static String docs(TopDocs td) {
    return Arrays.toString(Arrays.stream(td.scoreDocs).mapToInt(sd -> sd.doc).toArray());
  }

  /** Searching with the query must not change its equals/hashCode. */
  public void testSearchDoesNotModifyQuery() throws Exception {
    try (Directory dir = index(1, 50, 0);
        IndexReader reader = DirectoryReader.open(dir)) {
      PatienceKnnVectorQuery q = newQuery();
      PatienceKnnVectorQuery twin = newQuery();
      assertEquals(q, twin);
      int hash = q.hashCode();
      new IndexSearcher(reader).search(q, 5);
      assertEquals("hashCode changed by search", hash, q.hashCode());
      assertEquals("no longer equal to an identical query", twin, q);
    }
  }

  /**
   * Search A (2-segment reader) pauses after its first leaf; search B runs the same query object
   * on another reader; A then resumes. A must return what it returns without B.
   */
  public void testConcurrentSearchesOfOneQueryOnTwoReaders() throws Exception {
    try (Directory dirA = index(2, 30, 0);
        Directory dirB = index(1, 30, 1000);
        IndexReader readerA = DirectoryReader.open(dirA);
        IndexReader readerB = DirectoryReader.open(dirB)) {
      assertEquals(2, readerA.leaves().size());
      String expected = docs(new IndexSearcher(readerA).search(newQuery(), 5));

      PatienceKnnVectorQuery shared = newQuery();
      CountDownLatch aPaused = new CountDownLatch(1);
      CountDownLatch bDone = new CountDownLatch(1);
      AtomicBoolean first = new AtomicBoolean(true);
      IndexSearcher searcherA = new IndexSearcher(readerA);
      // never times out; only used to pause search A once, inside its first leaf
      searcherA.setTimeout(
          new QueryTimeout() {
            @Override
            public boolean shouldExit() {
              if (first.compareAndSet(true, false)) {
                aPaused.countDown();
                try {
                  bDone.await();
                } catch (InterruptedException e) {
                  throw new RuntimeException(e);
                }
              }
              return false;
            }
          });
      AtomicReference<Object> outcomeA = new AtomicReference<>();
      Thread threadA =
          new Thread(
              () -> {
                try {
                  outcomeA.set(searcherA.search(shared, 5));
                } catch (Throwable t) {
                  outcomeA.set(t);
                }
              },
              "search-A");
      threadA.start();
      aPaused.await();
      new IndexSearcher(readerB).search(shared, 5);
      bDone.countDown();
      threadA.join();
      if (outcomeA.get() instanceof Throwable t) {
        throw new AssertionError("search A failed after a concurrent search of the same query", t);
      }
      assertEquals(expected, docs((TopDocs) outcomeA.get()));
    }
  }
}
