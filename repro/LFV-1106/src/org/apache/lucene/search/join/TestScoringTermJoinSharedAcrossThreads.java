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
package org.apache.lucene.search.join;

import java.util.Locale;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * IndexSearcher is "completely thread safe" and "searching does not modify a Query" (Weight
 * javadoc). A scoring term join (JoinUtil.createJoinQuery with a ScoreMode other than None)
 * searched without scores builds a new TermsQuery over the join's own BytesRefHash, whose
 * constructor calls BytesRefHash#sort() again: sort() reorders, in place, the hash's ids array,
 * which the join also iterates as its sorted term ords. One thread scores, one thread counts, the
 * same query object and IndexSearcher; every document matches the join.
 */
public class TestScoringTermJoinSharedAcrossThreads extends LuceneTestCase {

  private static final int NUM_DOCS = 20_000;

  public void testScoringAndCountingThreadsShareOneJoinQuery() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < NUM_DOCS; i++) {
          // keys inserted out of sorted order
          String key = String.format(Locale.ROOT, "k%06d", (int) ((i * 7919L) % NUM_DOCS));
          Document doc = new Document();
          doc.add(new StringField("to", key, Field.Store.NO));
          doc.add(new SortedDocValuesField("from", new BytesRef(key)));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query join =
            JoinUtil.createJoinQuery(
                "from", false, "to", new MatchAllDocsQuery(), searcher, ScoreMode.Max);
        AtomicInteger minHits = new AtomicInteger(NUM_DOCS);
        CyclicBarrier start = new CyclicBarrier(2);
        Thread counting =
            new Thread(
                () -> {
                  try {
                    start.await();
                    for (int i = 0; i < 20; i++) {
                      searcher.count(join);
                    }
                  } catch (Exception e) {
                    throw new RuntimeException(e);
                  }
                });
        counting.start();
        start.await();
        for (int i = 0; i < 20; i++) {
          minHits.accumulateAndGet(searcher.search(join, NUM_DOCS).scoreDocs.length, Math::min);
        }
        counting.join();
        assertEquals("fewest hits of a scoring search", NUM_DOCS, minHits.get());
      }
    }
  }
}
