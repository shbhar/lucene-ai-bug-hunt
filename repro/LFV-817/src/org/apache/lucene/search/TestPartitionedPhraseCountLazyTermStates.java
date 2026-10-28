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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * End-to-end: an IndexSearcher with intra-segment concurrency (8 partitions of one segment, 8
 * threads) counts the matches of a non-scoring PhraseQuery and must agree with a sequential
 * IndexSearcher on the same reader. PhraseQuery#createWeight builds lazy TermStates when scores
 * are not needed, so the partitions of the segment share TermStates#get for every phrase term.
 * The collector manager only lines the partitions up (each partition waits, bounded, in
 * getLeafCollector until all partitions of the segment arrived, which IndexSearcher#searchLeaf
 * calls just before Weight#scorerSupplier); it does not change what is collected.
 */
public class TestPartitionedPhraseCountLazyTermStates extends LuceneTestCase {

  private static final int PARTS = 8;

  public void testPartitionedCountEqualsSequentialCount() throws Exception {
    int searches = Integer.getInteger("h292.searches", 300_000);
    String[] phrase = new String[12];
    for (int k = 0; k < phrase.length; k++) phrase[k] = String.format(Locale.ROOT, "p%02d", k);
    try (Directory dir = FSDirectory.open(createTempDir("h292e2e"))) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 2000; i++) {
          StringBuilder text = new StringBuilder("a" + (i % 3));
          for (int k = 0; k < phrase.length; k++) {
            // every 4th document holds the phrase in order, the others a permutation of it
            text.append(' ').append(phrase[i % 4 == 0 ? k : (k * 5 + i) % phrase.length]);
          }
          Document d = new Document();
          d.add(new TextField("body", text.toString(), Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      ExecutorService executor = Executors.newFixedThreadPool(PARTS);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        Query query = new PhraseQuery("body", phrase);
        IndexSearcher sequential = new IndexSearcher(reader);
        sequential.setQueryCache(null);
        IndexSearcher partitioned =
            new IndexSearcher(reader, executor) {
              @Override
              protected LeafSlice[] slices(List<LeafReaderContext> leaves) {
                List<LeafSlice> slices = new ArrayList<>();
                for (LeafReaderContext ctx : leaves) {
                  int maxDoc = ctx.reader().maxDoc(), per = maxDoc / PARTS;
                  for (int i = 0; i < PARTS; i++) {
                    int to = i == PARTS - 1 ? maxDoc : (i + 1) * per;
                    slices.add(
                        new LeafSlice(
                            new ArrayList<>(
                                List.of(LeafReaderContextPartition.createFromAndTo(ctx, i * per, to)))));
                  }
                }
                return slices.toArray(new LeafSlice[0]);
              }
            };
        partitioned.setQueryCache(null);
        assertEquals(PARTS, partitioned.getSlices().length);
        int expected = sequential.count(query);
        assertEquals(500, expected);

        int wrong = 0;
        String first = null;
        for (int i = 0; i < searches; i++) {
          String outcome;
          try {
            int got =
                partitioned.search(
                    query, aligned(new TotalHitCountCollectorManager(partitioned.getSlices())));
            if (got == expected) continue;
            outcome = "count " + got + " instead of " + expected;
          } catch (RuntimeException | AssertionError e) {
            StackTraceElement[] st = e.getStackTrace();
            outcome = e + (st.length > 0 ? " at " + st[0] : "");
          }
          wrong++;
          if (first == null) first = "search " + i + ": " + outcome;
        }
        System.out.println("searches=" + searches + " wrong=" + wrong + " first=" + first);
        assertEquals(
            "partitioned search differed from the sequential one in "
                + wrong
                + " of "
                + searches
                + " searches; first: "
                + first,
            0,
            wrong);
      } finally {
        executor.shutdown();
        assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
      }
    }
  }

  /** Lets the partitions of one segment enter scorerSupplier together (bounded wait). */
  private static <C extends Collector, T> CollectorManager<Collector, T> aligned(
      CollectorManager<C, T> in) {
    Map<Object, AtomicInteger> arrivals = new ConcurrentHashMap<>();
    List<C> made = new ArrayList<>();
    return new CollectorManager<>() {
      @Override
      public synchronized Collector newCollector() throws IOException {
        C c = in.newCollector();
        made.add(c);
        return new FilterCollector(c) {
          @Override
          public LeafCollector getLeafCollector(LeafReaderContext context) throws IOException {
            AtomicInteger n = arrivals.computeIfAbsent(context.id(), k -> new AtomicInteger());
            n.incrementAndGet();
            long deadline = System.nanoTime() + TimeUnit.MICROSECONDS.toNanos(200);
            while (n.get() < PARTS && System.nanoTime() < deadline) Thread.onSpinWait();
            return super.getLeafCollector(context);
          }
        };
      }

      @Override
      public synchronized T reduce(Collection<Collector> collectors) throws IOException {
        return in.reduce(made);
      }
    };
  }
}
