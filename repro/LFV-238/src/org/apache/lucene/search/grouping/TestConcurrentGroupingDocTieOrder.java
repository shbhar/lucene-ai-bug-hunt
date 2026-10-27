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
package org.apache.lucene.search.grouping;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * With an executor, two-pass grouping (GroupingSearch(String) -> TopGroupsCollectorManager ->
 * TopGroups.merge) orders documents that tie on the within-group sort by slice index instead of
 * by doc ID. Sequential search returns the lower doc ID first, as Sort and TopScoreDocCollector
 * document ("ties ... docID ascending"); concurrent search returns a different document.
 *
 * <p>Index: 6 segments. Segment 0 holds one document (doc 0); segments 1..5 hold two documents
 * each (docs 1..10). Every document is in group "g". IndexSearcher's default slicing sorts
 * segments by size, so segments 1..5 form slice 0 and segment 0 forms slice 1. All documents
 * score 1.0 for MatchAllDocsQuery.
 */
public class TestConcurrentGroupingDocTieOrder extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private ExecutorService executor;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int[] segmentSizes = {1, 2, 2, 2, 2, 2};
      for (int size : segmentSizes) {
        for (int i = 0; i < size; i++) {
          Document d = new Document();
          d.add(new SortedDocValuesField("group", new BytesRef("g")));
          w.addDocument(d);
        }
        w.flush();
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals(6, reader.leaves().size());
    executor = Executors.newFixedThreadPool(2, new NamedThreadFactory("TestDocTieOrder"));
  }

  @Override
  public void tearDown() throws Exception {
    executor.shutdown();
    assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static int[] docs(TopGroups<?> tg) {
    assertEquals(1, tg.groups.length);
    return Arrays.stream(tg.groups[0].scoreDocs()).mapToInt(sd -> sd.doc).toArray();
  }

  private TopGroups<?> search(IndexSearcher searcher, int groupDocsLimit) throws Exception {
    GroupingSearch gs = new GroupingSearch("group"); // default sorts: Sort.RELEVANCE
    gs.setGroupDocsLimit(groupDocsLimit);
    return gs.search(searcher, new MatchAllDocsQuery(), 0, 10);
  }

  public void testTopDocOfGroupWithExecutor() throws Exception {
    IndexSearcher sequential = new IndexSearcher(reader);
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    assertEquals(2, concurrent.getSlices().length);
    assertEquals("segment 0 must be in the last slice", 0, concurrent.getSlices()[1].partitions[0].ctx.docBase);

    int[] seq = docs(search(sequential, 1));
    assertArrayEquals("sequential", new int[] {0}, seq);
    int[] conc = docs(search(concurrent, 1));
    assertArrayEquals("concurrent (ties must be broken by doc ID)", new int[] {0}, conc);
  }

  public void testDocOrderWithinGroupWithExecutor() throws Exception {
    IndexSearcher sequential = new IndexSearcher(reader);
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    int[] seq = docs(search(sequential, 3));
    assertArrayEquals("sequential", new int[] {0, 1, 2}, seq);
    int[] conc = docs(search(concurrent, 3));
    assertArrayEquals("concurrent (ties must be broken by doc ID)", seq, conc);
  }

  /**
   * Same search through the public collector managers, as the package documentation describes
   * (also available in 10.5.x, where GroupingSearch itself still searched sequentially).
   */
  public void testCollectorManagersWithExecutor() throws Exception {
    IndexSearcher sequential = new IndexSearcher(reader);
    IndexSearcher concurrent = new IndexSearcher(reader, executor);
    int[] seq = managers(sequential);
    assertArrayEquals("sequential", new int[] {0, 1, 2}, seq);
    int[] conc = managers(concurrent);
    assertArrayEquals("concurrent (ties must be broken by doc ID)", seq, conc);
  }

  private static int[] managers(IndexSearcher searcher) throws Exception {
    Sort sort = Sort.RELEVANCE;
    Collection<SearchGroup<BytesRef>> topGroups =
        searcher.search(
            new MatchAllDocsQuery(),
            new FirstPassGroupingCollectorManager<>(
                () -> new TermGroupSelector("group"), sort, 0, 10));
    TopGroups<BytesRef> tg =
        searcher.search(
            new MatchAllDocsQuery(),
            new TopGroupsCollectorManager<>(
                () -> new TermGroupSelector("group"), topGroups, sort, sort, 0, 3, true));
    // diagnostic only: the hits carry the slice number as shardIndex
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : tg.groups[0].scoreDocs()) {
      sb.append(sd.doc).append("(shardIndex=").append(sd.shardIndex).append(") ");
    }
    if (VERBOSE) {
      System.out.println("slices=" + searcher.getSlices().length + " hits: " + sb);
    }
    return docs(tg);
  }
}
