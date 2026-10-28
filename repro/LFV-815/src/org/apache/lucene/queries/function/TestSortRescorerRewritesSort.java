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

import java.util.Arrays;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Rescorer;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.SortRescorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SortRescorer is handed the IndexSearcher but re-sorts with the caller's Sort as given: it never
 * calls Sort#rewrite(IndexSearcher), which IndexSearcher#search(Query, int, Sort) does. Re-sorting
 * all first-pass hits by a Sort must give the same order and sort values as searching with that
 * Sort on the same searcher.
 */
public class TestSortRescorerRewritesSort extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private final Query queryA = new TermQuery(new Term("body", "a"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // doc 0: tf(a)=8 in a 10-term body; doc 1: tf(a)=1 in a 1-term body
      Document d0 = new Document();
      d0.add(new TextField("body", "a a a a a a a a x x", Field.Store.NO));
      w.addDocument(d0);
      Document d1 = new Document();
      d1.add(new TextField("body", "a", Field.Store.NO));
      w.addDocument(d1);
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private IndexSearcher searcher(Similarity sim) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setSimilarity(sim);
    return s;
  }

  private static String hits(TopDocs td) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      sb.append("doc").append(sd.doc).append(Arrays.toString(((FieldDoc) sd).fields)).append(' ');
    }
    return sb.toString().trim();
  }

  private void assertRescoreEqualsSortedSearch(IndexSearcher s, Rescorer rescorer, Sort sort)
      throws Exception {
    TopDocs firstPass = s.search(new MatchAllDocsQuery(), 10);
    String expected = hits(s.search(new MatchAllDocsQuery(), 10, sort));
    String actual = hits(rescorer.rescore(s, firstPass, 10));
    assertEquals(
        "re-sorting every hit by " + sort + " on a searcher with " + s.getSimilarity(),
        expected,
        actual);
  }

  /**
   * Silent: a sort on a legacy query() function (QueryValueSource viewed as a DoubleValuesSource)
   * needs the searcher to score with its Similarity. Without the rewrite the function scores with
   * a fresh default-Similarity searcher, so the rescored order and sort values differ.
   */
  public void testQueryFunctionSortUsesTheSearchersSimilarity() throws Exception {
    Sort sort =
        new Sort(new QueryValueSource(queryA, 0f).asDoubleValuesSource().getSortField(true));
    for (Similarity sim : new Similarity[] {new ClassicSimilarity(), new BM25Similarity(1.2f, 0f)}) {
      IndexSearcher s = searcher(sim);
      assertRescoreEqualsSortedSearch(s, new SortRescorer(sort), sort);
    }
  }

  /** Loud: a ValueSource sort field (SortField.Type.REWRITEABLE) works with IndexSearcher. */
  public void testValueSourceSortField() throws Exception {
    Sort sort = new Sort(new QueryValueSource(queryA, 0f).getSortField(true), SortField.FIELD_DOC);
    IndexSearcher s = searcher(new BM25Similarity());
    assertRescoreEqualsSortedSearch(s, new SortRescorer(sort), sort);
  }

  /** Loud: a query-score sort (DoubleValuesSource#fromQuery) works with IndexSearcher. */
  public void testQueryScoreSort() throws Exception {
    Sort sort = new Sort(DoubleValuesSource.fromQuery(queryA).getSortField(true));
    IndexSearcher s = searcher(new BM25Similarity());
    assertRescoreEqualsSortedSearch(s, new SortRescorer(sort), sort);
  }

  /**
   * Loud, through the expressions module: Expression#getRescorer builds its own Sort from the
   * bindings, so a binding to a query score cannot be rewritten by the caller.
   */
  public void testExpressionRescorerWithQueryScoreBinding() throws Exception {
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("q", DoubleValuesSource.fromQuery(queryA));
    var expr = JavascriptCompiler.compile("q * 2");
    Sort sort = new Sort(expr.getSortField(bindings, true));
    IndexSearcher s = searcher(new BM25Similarity());
    assertRescoreEqualsSortedSearch(s, expr.getRescorer(bindings), sort);
  }
}
