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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
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
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The facet module's two sorted-search helpers, FacetsCollectorManager#search(IndexSearcher, Query,
 * int, Sort, FacetsCollectorManager) and DrillSideways#search(DrillDownQuery, Query, FieldDoc,
 * int, Sort, boolean), collect the top hits with the caller's Sort as given: neither calls
 * Sort#rewrite(IndexSearcher), which IndexSearcher#search(Query, int, Sort) does. Their top hits
 * must equal those of the sorted search of the same query on the same searcher.
 */
public class TestFacetSortedSearchRewritesSort extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private final FacetsConfig config = new FacetsConfig();
  private final Query queryA = new TermQuery(new Term("body", "a"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      // doc 0: tf(a)=8 in a 10-term body; doc 1: tf(a)=1 in a 1-term body
      for (String body : new String[] {"a a a a a a a a x x", "a"}) {
        Document doc = new Document();
        doc.add(new TextField("body", body, Field.Store.NO));
        doc.add(new SortedSetDocValuesFacetField("color", "red"));
        w.addDocument(config.build(doc));
      }
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

  private static String hits(TopDocs td) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc sd : td.scoreDocs) {
      sb.append("doc").append(sd.doc).append(Arrays.toString(((FieldDoc) sd).fields)).append(' ');
    }
    return sb.toString().trim();
  }

  private static Sort queryFunctionSort(Query q) {
    // descending by the score of q under the searcher's Similarity (legacy query() function)
    return new Sort(new QueryValueSource(q, 0f).asDoubleValuesSource().getSortField(true));
  }

  private static Sort queryScoreSort(Query q) {
    return new Sort(DoubleValuesSource.fromQuery(q).getSortField(true), SortField.FIELD_DOC);
  }

  private IndexSearcher searcher(boolean classic) {
    IndexSearcher s = new IndexSearcher(reader);
    s.setSimilarity(classic ? new ClassicSimilarity() : new BM25Similarity());
    return s;
  }

  private void assertFacetsCollectorManagerSearch(IndexSearcher s, Sort sort) throws Exception {
    String expected = hits(s.search(new MatchAllDocsQuery(), 10, sort));
    String actual =
        hits(
            FacetsCollectorManager.search(
                    s, new MatchAllDocsQuery(), 10, sort, new FacetsCollectorManager())
                .topDocs());
    assertEquals("sorted by " + sort + " with " + s.getSimilarity(), expected, actual);
  }

  private void assertDrillSidewaysSearch(IndexSearcher s, Sort sort) throws Exception {
    DrillDownQuery ddq = new DrillDownQuery(config, new MatchAllDocsQuery());
    ddq.add("color", "red");
    String expected = hits(s.search(ddq, 10, sort));
    DrillSideways ds =
        new DrillSideways(s, config, new DefaultSortedSetDocValuesReaderState(reader, config));
    String actual = hits(ds.search(ddq, null, null, 10, sort, false).hits);
    assertEquals("sorted by " + sort + " with " + s.getSimilarity(), expected, actual);
  }

  /** Silent: a legacy query() function sort scores with the default Similarity. */
  public void testFacetsCollectorManagerQueryFunctionSort() throws Exception {
    assertFacetsCollectorManagerSearch(searcher(true), queryFunctionSort(queryA));
  }

  /** Silent: same, through DrillSideways. */
  public void testDrillSidewaysQueryFunctionSort() throws Exception {
    assertDrillSidewaysSearch(searcher(true), queryFunctionSort(queryA));
  }

  /** Loud: a query-score sort (DoubleValuesSource#fromQuery) works with IndexSearcher. */
  public void testFacetsCollectorManagerQueryScoreSort() throws Exception {
    assertFacetsCollectorManagerSearch(searcher(false), queryScoreSort(queryA));
  }

  /** Loud: same, through DrillSideways. */
  public void testDrillSidewaysQueryScoreSort() throws Exception {
    assertDrillSidewaysSearch(searcher(false), queryScoreSort(queryA));
  }
}
