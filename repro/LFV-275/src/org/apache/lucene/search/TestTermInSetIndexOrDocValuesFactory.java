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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KeywordField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * TermInSetQuery.newIndexOrDocValuesQuery(indexRewriteMethod, field, terms) accepts any rewrite
 * method for the index side and pairs it with a DOC_VALUES_REWRITE query. IndexOrDocValuesQuery
 * requires that "Both provided queries must match the same documents and give the same scores".
 * With a top-terms rewrite the two sides match different documents, so the query's hits depend on
 * which side IndexOrDocValuesQuery picks (top level vs. inside a selective conjunction); with
 * SCORING_BOOLEAN_REWRITE the scores differ, and explain() (always the doc-values side) disagrees
 * with the score. The tests accept either outcome the contract allows: the factory rejects such a
 * rewrite method, or the resulting query is consistent.
 */
public class TestTermInSetIndexOrDocValuesFactory extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;
  private static final List<BytesRef> TERMS = List.of(new BytesRef("a"), new BytesRef("b"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (int i = 0; i < 200; i++) {
        Document doc = new Document();
        doc.add(new KeywordField("f", i % 2 == 0 ? "a" : "b", Field.Store.NO));
        if (i < 2) {
          doc.add(new StringField("sel", "x", Field.Store.NO)); // a selective filter: docs 0 (a), 1 (b)
        }
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setQueryCache(null);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private Query factory(MultiTermQuery.RewriteMethod m) {
    try {
      return TermInSetQuery.newIndexOrDocValuesQuery(m, "f", TERMS);
    } catch (IllegalArgumentException e) {
      return null; // rejecting the rewrite method is an acceptable outcome
    }
  }

  private void assertSameDocsAtTopLevelAndInConjunction(Query q) throws IOException {
    Query sel = new TermQuery(new Term("sel", "x"));
    Query conj =
        new BooleanQuery.Builder()
            .add(q, BooleanClause.Occur.MUST)
            .add(sel, BooleanClause.Occur.FILTER)
            .build();
    // docs matched by q at top level, restricted to the filter
    Query topLevelThenFilter =
        new BooleanQuery.Builder()
            .add(((IndexOrDocValuesQuery) q).getIndexQuery(), BooleanClause.Occur.MUST)
            .add(sel, BooleanClause.Occur.FILTER)
            .build();
    // user-visible: the top-level hits of q (index side, the side IndexOrDocValuesQuery uses as a
    // lead) restricted to the filter must equal the hits of (q AND filter), where q is verified on
    // the doc-values side because the filter leads
    assertEquals(
        "q AND filter must equal (hits of q) AND filter",
        searcher.count(topLevelThenFilter),
        searcher.count(conj));
    IndexOrDocValuesQuery iodv = (IndexOrDocValuesQuery) q;
    assertEquals(
        "IndexOrDocValuesQuery sides must match the same documents",
        searcher.count(iodv.getIndexQuery()),
        searcher.count(iodv.getRandomAccessQuery()));
  }

  public void testControlConstantScoreRewrite() throws IOException {
    assertSameDocsAtTopLevelAndInConjunction(
        factory(MultiTermQuery.CONSTANT_SCORE_BLENDED_REWRITE));
  }

  public void testTopTermsRewrite() throws IOException {
    Query q = factory(new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(1));
    if (q != null) {
      assertSameDocsAtTopLevelAndInConjunction(q);
    }
  }

  public void testScoringBooleanRewriteExplainMatchesScore() throws IOException {
    Query q = factory(MultiTermQuery.SCORING_BOOLEAN_REWRITE);
    if (q == null) {
      return;
    }
    TopDocs td = searcher.search(q, 1);
    float score = td.scoreDocs[0].score;
    float explained = searcher.explain(q, td.scoreDocs[0].doc).getValue().floatValue();
    assertEquals("explain() must agree with the score", score, explained, 0f);
  }
}
