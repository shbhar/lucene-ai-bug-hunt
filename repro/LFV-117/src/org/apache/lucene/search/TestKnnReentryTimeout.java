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

import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Multi-segment kNN search re-runs ("re-enters") segments whose first-pass hits all made the global
 * top k, and replaces those segments' first-pass hits with the second-pass result. When the query
 * timeout fires after the first pass completed, the second pass returns early with nothing and the
 * hits the first pass already found are dropped: a later timeout returns fewer hits than an earlier
 * one, down to 0 hits. Same symptom as GitHub #14639 ("can then return 0 hits"), whose fix (#14640)
 * only skips the second pass when the first pass itself timed out.
 */
public class TestKnnReentryTimeout extends LuceneTestCase {

  /** Times out on the (limit+1)-th call to shouldExit(). */
  private static final class CountingTimeout implements QueryTimeout {
    final AtomicInteger calls = new AtomicInteger();
    final int limit;

    CountingTimeout(int limit) {
      this.limit = limit;
    }

    @Override
    public boolean shouldExit() {
      return calls.incrementAndGet() > limit;
    }
  }

  public void testLaterTimeoutNeverReturnsFewerHits() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.getDefaultCodec())
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        int x = 0;
        for (int s = 0; s < 3; s++) { // 3 segments of 2 docs
          for (int i = 0; i < 2; i++) {
            Document d = new Document();
            d.add(new KnnFloatVectorField("v", new float[] {x++, 1}, VectorSimilarityFunction.EUCLIDEAN));
            w.addDocument(d);
          }
          w.commit();
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        assertEquals(3, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new KnnFloatVectorQuery("v", new float[] {0, 1}, 10);
        CountingTimeout never = new CountingTimeout(Integer.MAX_VALUE);
        searcher.setTimeout(never);
        assertEquals(6, searcher.count(query));
        int totalChecks = never.calls.get();
        StringBuilder sweep = new StringBuilder();
        int previous = 0;
        for (int limit = 0; limit <= totalChecks; limit++) {
          searcher.setTimeout(new CountingTimeout(limit));
          int count = searcher.count(query);
          sweep.append(limit).append("->").append(count).append(' ');
          assertTrue(
              "allowing more work before the timeout returned fewer hits (timeout check limit -> hits): "
                  + sweep,
              count >= previous);
          previous = count;
        }
      }
    }
  }
}
