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
package org.apache.lucene.search.join;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * {@code Weight#matches(LeafReaderContext, int)} must return {@code null} exactly for the documents
 * the query does not match (Weight.java javadoc: "Returns Matches for a specific document, or null if
 * the document does not match the parent query").
 *
 * <p>ToChildBlockJoinQuery's weight and the weight of the scoring global-ordinals join
 * (JoinUtil.createJoinQuery with a ScoreMode other than None) extend FilterWeight and change the set
 * of matching documents in scorerSupplier(), but inherit FilterWeight#matches, which returns the
 * wrapped weight's Matches: the parent query's for ToChildBlockJoinQuery, and the "to"-side query's
 * for the join. Only public API is used.
 */
public class TestJoinWeightMatchesContract extends LuceneTestCase {

  /** Docs whose Weight#matches is non-null, compared with the docs the query actually returns. */
  private static void assertMatchesAgreeWithHits(IndexSearcher searcher, Query query)
      throws IOException {
    assertMatchesAgreeWithHits(
        searcher, query, org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES);
  }

  private static void assertMatchesAgreeWithHits(
      IndexSearcher searcher, Query query, org.apache.lucene.search.ScoreMode weightScoreMode)
      throws IOException {
    TreeSet<Integer> hits = new TreeSet<>();
    for (ScoreDoc sd : searcher.search(query, 1000).scoreDocs) {
      hits.add(sd.doc);
    }
    Weight weight = searcher.createWeight(searcher.rewrite(query), weightScoreMode, 1f);
    TreeSet<Integer> withMatches = new TreeSet<>();
    for (LeafReaderContext ctx : searcher.getIndexReader().leaves()) {
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        if (weight.matches(ctx, doc) != null) {
          withMatches.add(ctx.docBase + doc);
        }
      }
    }
    assertFalse("the query must match something for this test to be meaningful", hits.isEmpty());
    assertEquals(
        "documents with non-null Weight#matches ("
            + weightScoreMode
            + " weight) must be exactly the hits of "
            + query,
        hits,
        withMatches);
  }

  private static Document doc(String... kv) {
    Document d = new Document();
    for (int i = 0; i < kv.length; i += 2) {
      d.add(new StringField(kv[i], kv[i + 1], Field.Store.NO));
    }
    return d;
  }

  public void testToChildBlockJoinQueryMatches() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        // block 0: docs 0,1 are children, doc 2 is a red parent
        w.addDocuments(
            List.of(
                doc("type", "child", "size", "s"),
                doc("type", "child", "size", "m"),
                doc("type", "parent", "color", "red")));
        // block 1: doc 3 is a child, doc 4 is a blue parent
        w.addDocuments(
            List.of(doc("type", "child", "size", "l"), doc("type", "parent", "color", "blue")));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents =
            new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        Query toChildren =
            new ToChildBlockJoinQuery(new TermQuery(new Term("color", "red")), parents);
        // the query returns the two children of the red parent: docs 0 and 1
        assertMatchesAgreeWithHits(searcher, toChildren);
      }
    }
  }

  /**
   * The wrong answer propagates: a conjunction with a ToChildBlockJoinQuery clause reports no
   * Matches for its own hit, so positions of the other clause (what a matches-based highlighter
   * uses) are lost.
   */
  public void testConjunctionWithToChildBlockJoinClauseMatches() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocuments(
            List.of(
                doc("type", "child", "size", "s"),
                doc("type", "child", "size", "m"),
                doc("type", "parent", "color", "red")));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        BitSetProducer parents =
            new QueryBitSetProducer(new TermQuery(new Term("type", "parent")));
        Query q =
            new BooleanQuery.Builder()
                .add(
                    new ToChildBlockJoinQuery(new TermQuery(new Term("color", "red")), parents),
                    Occur.MUST)
                .add(new TermQuery(new Term("size", "s")), Occur.MUST)
                .build();
        assertEquals(1, searcher.count(q)); // doc 0
        Weight weight =
            searcher.createWeight(
                searcher.rewrite(q), org.apache.lucene.search.ScoreMode.COMPLETE_NO_SCORES, 1f);
        Matches m = weight.matches(reader.leaves().get(0), 0);
        assertNotNull("doc 0 is a hit of " + q + ", so Weight#matches must not be null", m);
        assertNotNull("the size:s clause matched doc 0", m.getMatches("size"));
      }
    }
  }

  /** A global-ordinals join index: "from" docs 0-1, "to" docs 2-4; text:x reaches only "a". */
  private static Directory joinIndex() throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      List<Document> docs = new ArrayList<>();
      docs.add(join(doc("type", "from", "text", "x"), "a"));
      docs.add(join(doc("type", "from", "text", "y"), "b"));
      docs.add(join(doc("type", "to"), "a"));
      docs.add(join(doc("type", "to"), "b"));
      docs.add(join(doc("type", "to"), "c"));
      w.addDocuments(docs);
    }
    return dir;
  }

  /** Scoring weight (what a top-k search creates): JoinUtil returns a GlobalOrdinalsWithScoreQuery. */
  public void testGlobalOrdinalsJoinWithScoreMatches() throws IOException {
    try (Directory dir = joinIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      IndexSearcher searcher = new IndexSearcher(reader);
      Query from = new TermQuery(new Term("text", "x"));
      Query to = new TermQuery(new Term("type", "to"));
      Query join = JoinUtil.createJoinQuery("join", from, to, searcher, ScoreMode.Max, null);
      // the query returns only the "to" doc with join value "a": doc 2
      assertMatchesAgreeWithHits(searcher, join, org.apache.lucene.search.ScoreMode.COMPLETE);
    }
  }

  /**
   * Non-scoring weight with min/max filtering: createWeight still returns the FilterWeight subclass
   * (GlobalOrdinalsWithScoreQuery.W), because GlobalOrdinalsQuery cannot apply min/max.
   */
  public void testGlobalOrdinalsJoinWithMinMaxMatches() throws IOException {
    try (Directory dir = joinIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      assertEquals(1, reader.leaves().size());
      IndexSearcher searcher = new IndexSearcher(reader);
      Query from = new TermQuery(new Term("text", "x"));
      Query to = new TermQuery(new Term("type", "to"));
      Query join =
          JoinUtil.createJoinQuery("join", from, to, searcher, ScoreMode.Max, null, 1, 1);
      // exactly one "from" doc (doc 0) points at "a", so the query returns doc 2 only
      assertMatchesAgreeWithHits(searcher, join);
    }
  }

  private static Document join(Document d, String value) {
    d.add(new SortedDocValuesField("join", new BytesRef(value)));
    return d;
  }
}
