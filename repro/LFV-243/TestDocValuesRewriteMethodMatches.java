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
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Weight#matches must be non-null exactly for the documents a query matches. Queries rewritten with
 * DocValuesRewriteMethod (SortedSetDocValuesField#newSlowSetQuery,
 * MultiTermQuery#DOC_VALUES_REWRITE) find their hits in the doc values, but answer matches() from
 * the postings, which a doc-values-only field does not have.
 */
public class TestDocValuesRewriteMethodMatches extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    // one segment, doc ids 0, 1, 2 in insertion order
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String tag : new String[] {"apple", "banana", "cherry"}) {
        Document doc = new Document();
        doc.add(new StringField("body", "fruit", Field.Store.NO));
        doc.add(new SortedSetDocValuesField("tags", new BytesRef(tag))); // doc values only
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query tagsBanana(boolean setQuery) {
    BytesRef banana = new BytesRef("banana");
    return setQuery
        ? SortedSetDocValuesField.newSlowSetQuery("tags", List.of(banana))
        : SortedSetDocValuesField.newSlowExactQuery("tags", banana);
  }

  private static Query bodyFruitAnd(Query tagsClause, BooleanClause.Occur occur) {
    return new BooleanQuery.Builder()
        .add(new TermQuery(new Term("body", "fruit")), BooleanClause.Occur.MUST)
        .add(tagsClause, occur)
        .build();
  }

  /** For every document: it is a hit of the search if and only if Weight#matches is non-null. */
  private void assertMatchesAgreeWithHits(Query query) throws IOException {
    Set<Integer> hits = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(query, 10).scoreDocs) {
      hits.add(sd.doc);
    }
    Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1);
    StringBuilder wrong = new StringBuilder();
    for (LeafReaderContext ctx : reader.leaves()) {
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        boolean hit = hits.contains(ctx.docBase + doc);
        boolean hasMatches = weight.matches(ctx, doc) != null;
        if (hit != hasMatches) {
          wrong
              .append(" doc ")
              .append(ctx.docBase + doc)
              .append(
                  hit
                      ? " is a hit but matches() is null;"
                      : " is not a hit but matches() is non-null;");
        }
      }
    }
    if (wrong.length() > 0) {
      fail(query + ", hits " + hits + ":" + wrong);
    }
  }

  public void testSetQuery() throws IOException {
    assertMatchesAgreeWithHits(tagsBanana(true));
  }

  public void testSetQueryAsFilterClause() throws IOException {
    assertMatchesAgreeWithHits(bodyFruitAnd(tagsBanana(true), BooleanClause.Occur.FILTER));
  }

  public void testSetQueryAsMustNotClause() throws IOException {
    assertMatchesAgreeWithHits(bodyFruitAnd(tagsBanana(true), BooleanClause.Occur.MUST_NOT));
  }

  public void testPrefixQueryWithDocValuesRewrite() throws IOException {
    assertMatchesAgreeWithHits(
        new PrefixQuery(new Term("tags", "ban"), MultiTermQuery.DOC_VALUES_REWRITE));
  }

  /** Lucene's own oracle: every hit must have non-null Matches. */
  public void testCheckHitsCheckMatches() throws IOException {
    CheckHits.checkMatches(tagsBanana(true), searcher);
  }

  /** Control: the same three shapes with the exact-value doc-values query on the same field. */
  public void testControlExactQuery() throws IOException {
    assertMatchesAgreeWithHits(tagsBanana(false));
    assertMatchesAgreeWithHits(bodyFruitAnd(tagsBanana(false), BooleanClause.Occur.FILTER));
    assertMatchesAgreeWithHits(bodyFruitAnd(tagsBanana(false), BooleanClause.Occur.MUST_NOT));
    CheckHits.checkMatches(tagsBanana(false), searcher);
  }
}
