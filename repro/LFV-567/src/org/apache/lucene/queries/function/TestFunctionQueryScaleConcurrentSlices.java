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
package org.apache.lucene.queries.function;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.queries.function.valuesource.MaxDocValueSource;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.queries.function.valuesource.ScaleFloatFunction;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.NamedThreadFactory;

/**
 * A FunctionQuery is searched with an IndexSearcher that has an executor. Its FunctionWeight
 * creates ONE ValueSource context map (ValueSource.newContext: "a new non-threadsafe context map",
 * an IdentityHashMap) and passes it to getValues for every segment, so the slices of the search use
 * it concurrently. ScaleFloatFunction.getValues puts its ScaleInfo into that map during the search.
 * Here the map already holds 21 entries after createWeight ("searcher" and the weights of 20
 * QueryValueSources), so that search-time put grows the map while other slices read "searcher"
 * and the weights from it.
 *
 * <p>Every search must return the same result as a sequential search. The second test does the same
 * with a FunctionRangeQuery, whose FunctionRangeWeight shares its context map in the same way.
 */
public class TestFunctionQueryScaleConcurrentSlices extends LuceneTestCase {

  /** 32 segments of 50 docs; body is text over w0..w23, num a numeric doc value. */
  private static Directory buildIndex(String[] words) throws Exception {
    Random r = new Random(7);
    Directory dir = new ByteBuffersDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig(new MockAnalyzer(random())).setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int s = 0; s < 32; s++) {
        for (int d = 0; d < 50; d++) {
          Document doc = new Document();
          StringBuilder sb = new StringBuilder();
          for (int k = 0; k < 1 + r.nextInt(6); k++) sb.append(words[r.nextInt(words.length)]).append(' ');
          doc.add(new TextField("body", sb.toString(), Field.Store.NO));
          doc.add(new NumericDocValuesField("num", r.nextInt(1000)));
          w.addDocument(doc);
        }
        w.flush();
      }
    }
    return dir;
  }

  /**
   * scale(num) + 20 query() functions + maxdoc(): after createWeight the shared context holds 21
   * entries ("searcher" and the 20 QueryValueSource weights).
   */
  private static ValueSource function(String[] words) {
    List<ValueSource> sources = new ArrayList<>();
    sources.add(new ScaleFloatFunction(new LongFieldSource("num"), 0, 1));
    for (int i = 0; i < 20; i++) {
      sources.add(new QueryValueSource(new TermQuery(new Term("body", words[i])), 0f));
    }
    sources.add(new MaxDocValueSource());
    return new SumFloatFunction(sources.toArray(new ValueSource[0]));
  }

  private interface Search {
    String run(IndexSearcher searcher) throws Exception;
  }

  /** Runs the search 300 times on a searcher with an executor and compares with a sequential run. */
  private static void checkConcurrentEqualsSequential(String what, Search search) throws Exception {
    checkConcurrentEqualsSequential(what, null, search);
  }

  private static void checkConcurrentEqualsSequential(
      String what, Similarity similarity, Search search) throws Exception {
    String[] words = new String[24];
    for (int i = 0; i < words.length; i++) words[i] = "w" + i;
    ExecutorService executor =
        Executors.newFixedThreadPool(8, new NamedThreadFactory("TestFunctionQueryScale"));
    try (Directory dir = buildIndex(words);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher sequential = new IndexSearcher(reader);
      sequential.setQueryCache(null);
      if (similarity != null) sequential.setSimilarity(similarity);
      // default slicing: 32 segments of 50 docs give 7 slices, searched by the 8 executor threads
      IndexSearcher concurrent = new IndexSearcher(reader, executor);
      concurrent.setQueryCache(null);
      if (similarity != null) concurrent.setSimilarity(similarity);
      String expected = search.run(sequential);
      List<String> failures = new ArrayList<>();
      int different = 0, threw = 0;
      for (int i = 0; i < 300; i++) {
        try {
          String got = search.run(concurrent);
          if (got.equals(expected) == false) {
            different++;
            if (failures.size() < 3) failures.add("search " + i + " returned [" + got + "], expected [" + expected + "]");
          }
        } catch (Throwable t) {
          threw++;
          if (failures.size() < 3) failures.add("search " + i + " threw " + t + (t.getCause() != null ? " caused by " + t.getCause() : ""));
        }
      }
      assertTrue(
          what + " searched with an executor, 300 searches: " + different + " returned a different result (no exception), " + threw + " threw; first: " + failures,
          different == 0 && threw == 0);
    } finally {
      executor.shutdown();
      assertTrue(executor.awaitTermination(1, TimeUnit.MINUTES));
    }
  }

  public void testScaleFunctionOnSearcherWithExecutor() throws Exception {
    String[] words = new String[24];
    for (int i = 0; i < words.length; i++) words[i] = "w" + i;
    Query q = new FunctionQuery(function(words));
    checkConcurrentEqualsSequential("FunctionQuery", s -> hits(s.search(q, 10)));
  }

  public void testScaleFunctionInFunctionRangeQueryOnSearcherWithExecutor() throws Exception {
    String[] words = new String[24];
    for (int i = 0; i < words.length; i++) words[i] = "w" + i;
    Query q = new FunctionRangeQuery(function(words), 1600f, 1601f, true, false);
    checkConcurrentEqualsSequential("FunctionRangeQuery", s -> "count=" + s.count(q));
  }

  /**
   * scale(num) + 20 query() functions only, on searchers with a non-default Similarity: when a slice
   * cannot find "searcher" in the shared context, QueryValueSource silently builds its weight on a
   * new IndexSearcher that has the default similarity.
   */
  public void testQueryFunctionsWithCustomSimilarityOnSearcherWithExecutor() throws Exception {
    String[] words = new String[24];
    for (int i = 0; i < words.length; i++) words[i] = "w" + i;
    List<ValueSource> sources = new ArrayList<>();
    sources.add(new ScaleFloatFunction(new LongFieldSource("num"), 0, 1));
    for (int i = 0; i < 20; i++) {
      sources.add(new QueryValueSource(new TermQuery(new Term("body", words[i])), 0f));
    }
    Query q = new FunctionQuery(new SumFloatFunction(sources.toArray(new ValueSource[0])));
    checkConcurrentEqualsSequential(
        "FunctionQuery with BM25Similarity(1.8, 0.3)", new BM25Similarity(1.8f, 0.3f), s -> hits(s.search(q, 10)));
  }

  private static String hits(TopDocs td) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) sb.append(sd.doc).append(':').append(Float.floatToIntBits(sd.score)).append(' ');
    return sb.toString();
  }
}
