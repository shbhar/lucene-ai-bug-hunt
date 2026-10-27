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
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.ReaderUtil;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * MultiTermQuery.DOC_VALUES_REWRITE (used by SortedSetDocValuesField#newSlowSetQuery and
 * SortedDocValuesField#newSlowSetQuery) matches documents on a doc-values-only field, but its
 * Weight#matches looks the matched terms up in the field's postings, which do not exist, so it
 * returns null for every matching document.
 *
 * <p>Weight#matches javadoc: "Returns Matches for a specific document, or null if the document does
 * not match the parent query. A query match that contains no position information (for example, a
 * Point or DocValues query) will return MatchesUtils#MATCH_WITH_NO_TERMS".
 */
public class TestDocValuesRewriteMatches extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (String v : new String[] {"apple", "banana", "cherry"}) {
        Document doc = new Document();
        doc.add(new StringField("body", "fruit", Field.Store.NO));
        // doc-values only: no postings for these fields
        doc.add(new SortedSetDocValuesField("tags", new BytesRef(v)));
        doc.add(new SortedDocValuesField("tag", new BytesRef(v)));
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

  private Matches matches(Query query, int globalDoc) throws IOException {
    Weight w = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1f);
    List<LeafReaderContext> leaves = reader.leaves();
    LeafReaderContext ctx = leaves.get(ReaderUtil.subIndex(globalDoc, leaves));
    return w.matches(ctx, globalDoc - ctx.docBase);
  }

  /** Every hit of a query must have non-null Matches (Lucene's own CheckHits oracle). */
  private void assertMatchesForEveryHit(Query query) throws IOException {
    TopDocs td = searcher.search(query, 10);
    assertTrue("query must match something: " + query, td.scoreDocs.length > 0);
    for (ScoreDoc sd : td.scoreDocs) {
      assertNotNull(
          "Weight#matches returned null for matching doc " + sd.doc + " of " + query,
          matches(query, sd.doc));
    }
    CheckHits.checkMatches(query, searcher);
  }

  public void testControlDocValuesRangeQueryHasMatches() throws IOException {
    // another doc-values query on the same field: matches() is MATCH_WITH_NO_TERMS for its hits
    assertMatchesForEveryHit(
        SortedSetDocValuesField.newSlowRangeQuery(
            "tags", new BytesRef("a"), new BytesRef("b"), true, true));
  }

  public void testSortedSetNewSlowSetQuery() throws IOException {
    assertMatchesForEveryHit(
        SortedSetDocValuesField.newSlowSetQuery(
            "tags", List.of(new BytesRef("apple"), new BytesRef("cherry"))));
  }

  public void testSortedNewSlowSetQuery() throws IOException {
    assertMatchesForEveryHit(
        SortedDocValuesField.newSlowSetQuery(
            "tag", List.of(new BytesRef("apple"), new BytesRef("cherry"))));
  }

  public void testPrefixQueryDocValuesRewrite() throws IOException {
    assertMatchesForEveryHit(new PrefixQuery(new Term("tags", "ban"), MultiTermQuery.DOC_VALUES_REWRITE));
  }

  public void testFilterClauseMakesWholeQueryUnmatched() throws IOException {
    // A doc-values-only filter clause inside a BooleanQuery: BooleanWeight#matches returns null as
    // soon as a required clause returns null, so the whole query reports no match for its hits.
    Query q =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term("body", "fruit")), BooleanClause.Occur.MUST)
            .add(
                SortedSetDocValuesField.newSlowSetQuery("tags", List.of(new BytesRef("banana"))),
                BooleanClause.Occur.FILTER)
            .build();
    assertMatchesForEveryHit(q);
  }
}
