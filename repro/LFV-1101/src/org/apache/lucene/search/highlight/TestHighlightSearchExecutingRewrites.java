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
package org.apache.lucene.search.highlight;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RescoreTopNQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The classic Highlighter rewrites queries it does not know against its one-document internal
 * reader, whose FieldInfos describe every field as the highlighted text field. Queries whose
 * rewrite runs a search there and consults the schema of their own non-text fields then throw
 * IllegalStateException, although the document matches the query.
 */
public class TestHighlightSearchExecutingRewrites extends LuceneTestCase {

  private static final String TEXT = "the quick brown fox";

  private static Directory index(Analyzer analyzer) throws Exception {
    Directory dir = newDirectory();
    FieldType tv = new FieldType(TextField.TYPE_STORED);
    tv.setStoreTermVectors(true);
    tv.setStoreTermVectorPositions(true);
    tv.setStoreTermVectorOffsets(true);
    tv.freeze();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      for (int i = 0; i < 3; i++) {
        Document d = new Document();
        d.add(new Field("body", i == 1 ? "a lazy dog" : TEXT, tv));
        d.add(new NumericDocValuesField("popularity", 10 + i));
        d.add(new KeywordField("tag", "news", Field.Store.NO));
        d.add(new KnnFloatVectorField("vec", new float[] {1f, i}));
        w.addDocument(d);
      }
    }
    return dir;
  }

  private static int firstHit(DirectoryReader reader, Query q) throws Exception {
    TopDocs td = new IndexSearcher(reader).search(q, 10);
    assertTrue(td.scoreDocs.length > 0); // the document matches the query
    return td.scoreDocs[0].doc;
  }

  /**
   * Text hits re-ranked by a doc-values field, highlighted from re-analysed text (the default
   * path): IllegalStateException 'unexpected docvalues type NONE for field 'popularity''.
   */
  public void testRescoreTopNByDocValuesReanalysed() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query q =
          new RescoreTopNQuery(
              new TermQuery(new Term("body", "fox")),
              DoubleValuesSource.fromLongField("popularity"),
              10);
      firstHit(reader, q);
      TokenStream ts = analyzer.tokenStream("body", TEXT);
      // an unsupported query may give no highlight, but must not make highlighting throw
      new Highlighter(new QueryScorer(q, "body")).getBestFragment(ts, TEXT);
    }
  }

  /**
   * A text clause plus a filtered kNN clause, highlighted from term vectors:
   * IllegalStateException from the FieldExistsQuery the kNN rewrite adds to its filter.
   */
  public void testFilteredKnnClauseTermVectors() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("body", "fox")), Occur.MUST)
              .add(
                  new KnnFloatVectorQuery(
                      "vec", new float[] {1f, 0f}, 3, KeywordField.newExactQuery("tag", "news")),
                  Occur.MUST)
              .build();
      int doc = firstHit(reader, q);
      TokenStream ts =
          TokenSources.getTermVectorTokenStreamOrNull("body", reader.termVectors().get(doc), -1);
      assertNotNull(ts);
      assertEquals(
          "the quick brown <B>fox</B>",
          new Highlighter(new QueryScorer(q, "body")).getBestFragment(ts, TEXT));
    }
  }

  /** Control: the same filtered kNN query over re-analysed text highlights "fox" (passes). */
  public void testFilteredKnnClauseReanalysed() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      Query q =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("body", "fox")), Occur.MUST)
              .add(
                  new KnnFloatVectorQuery(
                      "vec", new float[] {1f, 0f}, 3, KeywordField.newExactQuery("tag", "news")),
                  Occur.MUST)
              .build();
      firstHit(reader, q);
      TokenStream ts = analyzer.tokenStream("body", TEXT);
      assertEquals(
          "the quick brown <B>fox</B>",
          new Highlighter(new QueryScorer(q, "body")).getBestFragment(ts, TEXT));
    }
  }
}
