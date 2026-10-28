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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.function.FunctionRangeQuery;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.queries.mlt.MoreLikeThisQuery;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * IndexWriter#deleteDocuments(Query...) says it "Deletes the document(s) matching any of the
 * provided queries". Each test builds a small index of two segments, takes the documents that
 * IndexSearcher#search(query) returns on that index, then deletes with the same query in a new
 * IndexWriter and commits. The deleted documents should be exactly the searched ones. A TermQuery
 * control passes; queries whose matches depend on the rest of the index (kNN top-k, the default
 * FuzzyQuery top-50 rewrite, top-N MultiTermQuery rewrites, CommonTermsQuery, MoreLikeThisQuery and
 * a score-range predicate) delete a different set, because the delete is resolved on each segment
 * separately.
 */
public class TestDeleteByQueryPerSegmentEvaluation extends LuceneTestCase {

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  /** Builds one segment per inner list (flushed by commit). */
  private static Directory buildSegments(List<List<Document>> segments) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, config())) {
      for (List<Document> segment : segments) {
        for (Document doc : segment) {
          w.addDocument(doc);
        }
        w.commit();
      }
    }
    return dir;
  }

  private static TreeSet<Integer> searchIds(Directory dir, Query query) throws IOException {
    TreeSet<Integer> ids = new TreeSet<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      StoredFields storedFields = searcher.storedFields();
      for (ScoreDoc sd : searcher.search(query, Math.max(1, reader.maxDoc())).scoreDocs) {
        ids.add(storedFields.document(sd.doc).getField("id").numericValue().intValue());
      }
    }
    return ids;
  }

  private static TreeSet<Integer> liveIds(Directory dir) throws IOException {
    TreeSet<Integer> ids = new TreeSet<>();
    try (DirectoryReader reader = DirectoryReader.open(dir)) {
      for (LeafReaderContext ctx : reader.leaves()) {
        LeafReader leaf = ctx.reader();
        Bits liveDocs = leaf.getLiveDocs();
        StoredFields storedFields = leaf.storedFields();
        for (int doc = 0; doc < leaf.maxDoc(); doc++) {
          if (liveDocs == null || liveDocs.get(doc)) {
            ids.add(storedFields.document(doc).getField("id").numericValue().intValue());
          }
        }
      }
    }
    return ids;
  }

  /** Deletes with the query in a new writer, commits, and compares with the searched ids. */
  private static void assertDeletesExactlySearchHits(Directory dir, Query query, int expectedHits)
      throws IOException {
    TreeSet<Integer> before = liveIds(dir);
    TreeSet<Integer> searched = searchIds(dir, query);
    assertEquals("search hits for " + query, expectedHits, searched.size());
    try (IndexWriter w = new IndexWriter(dir, config())) {
      w.deleteDocuments(query);
      w.commit();
    }
    TreeSet<Integer> deleted = new TreeSet<>(before);
    deleted.removeAll(liveIds(dir));
    assertEquals(
        "deleteDocuments(" + query + ") must delete exactly the documents search returns",
        searched,
        deleted);
  }

  private static Document doc(int id) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    return doc;
  }

  /** Control: a query whose matches depend only on each document. Passes. */
  public void testTermQueryControl() throws IOException {
    List<List<Document>> segments = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> segment = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        Document doc = doc(s * 3 + i);
        doc.add(new StringField("color", i == 0 ? "red" : "blue", Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(dir, new TermQuery(new Term("color", "red")), 2);
    }
  }

  /** k=2 nearest neighbours: search returns 2 documents, the delete removes 2 per segment. */
  public void testKnnFloatVectorQuery() throws IOException {
    List<List<Document>> segments = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> segment = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        int id = s * 3 + i;
        Document doc = doc(id);
        doc.add(
            new KnnFloatVectorField(
                "vector", new float[] {id + 1, 1f}, VectorSimilarityFunction.EUCLIDEAN));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(
          dir, new KnnFloatVectorQuery("vector", new float[] {0f, 1f}, 2), 2);
    }
  }

  /** Distinct one-substitution or two-substitution variants of "lucenex". */
  private static List<String> variants(int edits, int count) {
    List<String> out = new ArrayList<>();
    String base = "lucenex";
    String letters = "bcdfghjkmnpqrstvwz";
    for (int p = 0; p < base.length() && out.size() < count; p++) {
      for (int l = 0; l < letters.length() && out.size() < count; l++) {
        char[] c = base.toCharArray();
        c[p] = letters.charAt(l);
        if (edits == 2) {
          c[(p + 2) % base.length()] = 'y';
        }
        String s = new String(c);
        if (s.equals(base) == false && out.contains(s) == false) {
          out.add(s);
        }
      }
    }
    return out;
  }

  /**
   * Default FuzzyQuery (maxExpansions=50): segment 1 has 50 terms at edit distance 1, segment 2 has
   * 10 terms at edit distance 2. Search keeps the 50 best terms of the index (the distance-1 ones);
   * the delete keeps the 50 best terms of each segment and also removes the 10 distance-2 documents.
   */
  public void testDefaultFuzzyQuery() throws IOException {
    List<String> near = variants(1, 50);
    List<String> far = variants(2, 10);
    assertEquals(50, near.size());
    assertEquals(10, far.size());
    List<List<Document>> segments = new ArrayList<>();
    int id = 0;
    for (List<String> names : List.of(near, far)) {
      List<Document> segment = new ArrayList<>();
      for (String name : names) {
        Document doc = doc(id++);
        doc.add(new StringField("name", name, Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(dir, new FuzzyQuery(new Term("name", "lucenex")), 50);
    }
  }

  /** A top-N MultiTermQuery rewrite keeps the N best terms per segment instead of per index. */
  public void testTopTermsRewrite() throws IOException {
    List<List<Document>> segments = new ArrayList<>();
    for (int s = 0; s < 2; s++) {
      List<Document> segment = new ArrayList<>();
      for (int i = 0; i < 3; i++) {
        int id = s * 3 + i;
        Document doc = doc(id);
        doc.add(new StringField("p", "pre" + id, Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(
          dir,
          new PrefixQuery(
              new Term("p", "pre"), new MultiTermQuery.TopTermsScoringBooleanQueryRewrite(2)),
          2);
    }
  }

  /**
   * CommonTermsQuery classifies terms as high or low frequency by docFreq / maxDoc. Index-wide
   * "common" (5 of 8 documents) is high-frequency, so only "rare" is required; in the second
   * segment "common" is rare (1 of 4), so the delete also removes documents that only contain it.
   */
  public void testCommonTermsQuery() throws IOException {
    String[][] text = {{"common rare", "common", "common", "common"}, {"common", "rare", "x", "x"}};
    List<List<Document>> segments = new ArrayList<>();
    int id = 0;
    for (String[] segmentText : text) {
      List<Document> segment = new ArrayList<>();
      for (String t : segmentText) {
        Document doc = doc(id++);
        doc.add(new TextField("t", t, Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    CommonTermsQuery query = new CommonTermsQuery(Occur.SHOULD, Occur.SHOULD, 0.5f);
    query.add(new Term("t", "common"));
    query.add(new Term("t", "rare"));
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(dir, query, 2);
    }
  }

  /**
   * MoreLikeThisQuery (default minDocFreq 5): "zeta" is in 6 documents of the index, 3 per segment,
   * so search uses it and the per-segment delete drops it.
   */
  public void testMoreLikeThisQuery() throws IOException {
    List<List<Document>> segments = new ArrayList<>();
    int id = 0;
    for (int s = 0; s < 2; s++) {
      List<Document> segment = new ArrayList<>();
      for (int i = 0; i < 6; i++) {
        Document doc = doc(id++);
        doc.add(new TextField("body", i < 3 ? "zeta" : "other", Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      assertDeletesExactlySearchHits(
          dir,
          new MoreLikeThisQuery("zeta", new String[] {"body"}, new StandardAnalyzer(), "body"),
          6);
    }
  }

  /**
   * A score-range predicate (the default BM25Similarity everywhere): the bounds are the scores the
   * searcher gives to "t:common"; the delete scores each segment with that segment's statistics.
   */
  public void testScoreRangePredicate() throws IOException {
    String[][] text = {{"common", "common x", "common", "x"}, {"common", "x", "x", "x"}};
    List<List<Document>> segments = new ArrayList<>();
    int id = 0;
    for (String[] segmentText : text) {
      List<Document> segment = new ArrayList<>();
      for (String t : segmentText) {
        Document doc = doc(id++);
        doc.add(new TextField("t", t, Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    try (Directory dir = buildSegments(segments)) {
      TermQuery common = new TermQuery(new Term("t", "common"));
      float lo = Float.MAX_VALUE;
      float hi = -Float.MAX_VALUE;
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        for (ScoreDoc sd : new IndexSearcher(reader).search(common, 10).scoreDocs) {
          lo = Math.min(lo, sd.score);
          hi = Math.max(hi, sd.score);
        }
      }
      assertDeletesExactlySearchHits(
          dir, new FunctionRangeQuery(new QueryValueSource(common, 0f), lo, hi, true, true), 4);
    }
  }
}
