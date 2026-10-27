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
package org.apache.lucene.facet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesReaderState;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.ExitableDirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.QueryTimeout;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A failing drill-down or drill-sideways query should surface the same exception whether
 * DrillSideways runs sequentially or with an ExecutorService (the class javadoc presents the two
 * as interchangeable implementations). With an executor, DrillSideways#searchConcurrently wraps
 * every task failure as RuntimeException(ExecutionException(cause)).
 */
public class TestDrillSidewaysConcurrentExceptionType extends LuceneTestCase {

  private static final FacetsConfig CONFIG = new FacetsConfig();

  private static Directory index() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      String[] colors = {"red", "green", "blue"};
      for (int i = 0; i < 30; i++) {
        Document doc = new Document();
        doc.add(new TextField("body", "word" + (i % 7) + " common", Field.Store.NO));
        doc.add(new SortedSetDocValuesFacetField("color", colors[i % 3]));
        w.addDocument(CONFIG.build(doc));
      }
    }
    return dir;
  }

  /** More leaf clauses than IndexSearcher.getMaxClauseCount(), nested so that it can be built. */
  private static Query tooManyNestedClauses() {
    BooleanQuery.Builder outer = new BooleanQuery.Builder();
    int perInner = IndexSearcher.getMaxClauseCount() / 2 + 1;
    for (int b = 0; b < 2; b++) {
      BooleanQuery.Builder inner = new BooleanQuery.Builder();
      for (int i = 0; i < perInner; i++) {
        inner.add(new TermQuery(new Term("body", "t" + b + "_" + i)), BooleanClause.Occur.SHOULD);
      }
      outer.add(inner.build(), BooleanClause.Occur.SHOULD);
    }
    return outer.build();
  }

  private static Throwable search(DrillSideways ds, Query base) {
    DrillDownQuery q = new DrillDownQuery(CONFIG, base);
    q.add("color", "red");
    try {
      ds.search(q, 10);
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  public void testTooManyClausesSameExceptionWithExecutor() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, CONFIG);

      Throwable sequential = search(new DrillSideways(searcher, CONFIG, state), tooManyNestedClauses());
      assertTrue(String.valueOf(sequential), sequential instanceof IndexSearcher.TooManyClauses);

      Throwable concurrent =
          search(new DrillSideways(searcher, CONFIG, null, state, executor), tooManyNestedClauses());
      assertTrue(
          "sequential threw " + sequential.getClass().getName() + ", with an executor: " + concurrent,
          concurrent instanceof IndexSearcher.TooManyClauses);
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }

  public void testQueryTimeoutSameExceptionWithExecutor() throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(2);
    AtomicBoolean timedOut = new AtomicBoolean(false);
    QueryTimeout timeout = timedOut::get;
    try (Directory dir = index();
        DirectoryReader reader = ExitableDirectoryReader.wrap(DirectoryReader.open(dir), timeout)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      SortedSetDocValuesReaderState state = new DefaultSortedSetDocValuesReaderState(reader, CONFIG);
      timedOut.set(true); // every later timeout check fires
      Query base = new PrefixQuery(new Term("body", "word"));

      Throwable sequential = search(new DrillSideways(searcher, CONFIG, state), base);
      assertTrue(
          String.valueOf(sequential),
          sequential instanceof ExitableDirectoryReader.ExitingReaderException);

      Throwable concurrent = search(new DrillSideways(searcher, CONFIG, null, state, executor), base);
      assertTrue(
          "sequential threw " + sequential.getClass().getName() + ", with an executor: " + concurrent,
          concurrent instanceof ExitableDirectoryReader.ExitingReaderException);
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
    }
  }
}
