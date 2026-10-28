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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * GroupingSearch#setCaching and #setCachingInMB document a limit for the whole second-pass cache:
 * "The cache will not contain more than the maximum specified documents ... If the cache grows
 * beyond the specified limit, then the cache is purged and not used in the second pass search."
 *
 * <p>With an IndexSearcher that has an executor, GroupingSearch caches through
 * CachingCollectorManager, which applies the limit to each slice. A query that matches more
 * documents than the limit is then replayed from a cache that holds all of them, instead of being
 * re-run. The test counts how often the query is executed: 2 means the cache was not used (the
 * documented behaviour once the limit is exceeded), 1 means the cache was replayed.
 */
public class TestGroupingSearchCacheLimitPerSlice extends LuceneTestCase {

  private static final int SEGMENTS = 10;
  private static final int DOCS_PER_SEGMENT = 100;
  private static final int MATCHING = SEGMENTS * DOCS_PER_SEGMENT; // every document matches

  /** Counts how many times the query is executed (one Weight per search). */
  private static final class CountingQuery extends Query {
    final Query in;
    final AtomicInteger executions;

    CountingQuery(Query in, AtomicInteger executions) {
      this.in = in;
      this.executions = executions;
    }

    @Override
    public Weight createWeight(IndexSearcher searcher, ScoreMode scoreMode, float boost)
        throws IOException {
      executions.incrementAndGet();
      return in.createWeight(searcher, scoreMode, boost);
    }

    @Override
    public Query rewrite(IndexSearcher searcher) throws IOException {
      Query rewritten = in.rewrite(searcher);
      return rewritten == in ? this : new CountingQuery(rewritten, executions);
    }

    @Override
    public void visit(QueryVisitor visitor) {
      in.visit(visitor);
    }

    @Override
    public String toString(String field) {
      return "counting(" + in.toString(field) + ")";
    }

    @Override
    public boolean equals(Object other) {
      return sameClassAs(other) && in.equals(((CountingQuery) other).in);
    }

    @Override
    public int hashCode() {
      return 31 * classHash() + in.hashCode();
    }
  }

  private static Directory buildIndex() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int id = 0;
      for (int s = 0; s < SEGMENTS; s++) {
        for (int i = 0; i < DOCS_PER_SEGMENT; i++) {
          Document doc = new Document();
          doc.add(new StringField("body", "match", Field.Store.NO));
          doc.add(new SortedDocValuesField("author", new BytesRef("author" + (id++ % 7))));
          w.addDocument(doc);
        }
        w.flush();
      }
    }
    return dir;
  }

  /** Runs one grouped search and returns how many times the query was executed. */
  private static int executions(IndexSearcher searcher, GroupingSearch groupingSearch)
      throws IOException {
    AtomicInteger executions = new AtomicInteger();
    Query query = new CountingQuery(new TermQuery(new Term("body", "match")), executions);
    TopGroups<BytesRef> groups = groupingSearch.search(searcher, query, 0, 10);
    assertEquals(MATCHING, groups.totalHitCount);
    return executions.get();
  }

  private void assertLimitBoundsWholeSearch(GroupingSearch groupingSearch, String setting)
      throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(SEGMENTS, reader.leaves().size());

      // Control: without an executor the single cache exceeds the limit, so the query is re-run.
      IndexSearcher sequential = new IndexSearcher(reader);
      assertEquals(
          setting + " on a sequential searcher: query matching " + MATCHING + " docs is re-run",
          2,
          executions(sequential, groupingSearch));

      ExecutorService executor =
          Executors.newFixedThreadPool(2, new NamedThreadFactory("grouping-cache-limit"));
      try {
        // Default slicing: 10 segments of 100 docs -> 2 slices of 500 docs each.
        IndexSearcher concurrent = new IndexSearcher(reader, executor);
        assertEquals(2, concurrent.getSlices().length);
        assertEquals(
            setting
                + " with an executor ("
                + concurrent.getSlices().length
                + " slices): the query matches "
                + MATCHING
                + " documents, more than the limit, so the cache must be purged and the query"
                + " re-run (2 executions); 1 execution means a cache holding all "
                + MATCHING
                + " documents was replayed",
            2,
            executions(concurrent, groupingSearch));
      } finally {
        TestUtil.shutdownExecutorService(executor);
      }
    }
  }

  public void testSetCachingLimitIsForTheWholeSearch() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    groupingSearch.setCaching(600, true); // 600 < 1000 matching documents
    assertLimitBoundsWholeSearch(groupingSearch, "setCaching(600, true)");
  }

  public void testSetCachingInMBLimitIsForTheWholeSearch() throws IOException {
    GroupingSearch groupingSearch = new GroupingSearch("author");
    // 0.005 MB = 5242 bytes; with scores CachingCollector uses 8 bytes per document, so the limit
    // is 655 documents, while 1000 documents (8000 bytes) match.
    groupingSearch.setCachingInMB(0.005, true);
    assertLimitBoundsWholeSearch(groupingSearch, "setCachingInMB(0.005, true)");
  }
}
