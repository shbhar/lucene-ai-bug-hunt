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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * IndexWriter#deleteDocuments(Query...) "Deletes the document(s) matching any of the provided
 * queries". Each test indexes one segment per inner list, takes the documents that
 * IndexSearcher#search(query) returns, deletes with the same query in the same writer, commits and
 * compares. Per-document queries pass; queries whose matches depend on the other documents of the
 * index (kNN top-k, the default FuzzyQuery's top-50 term expansion) delete a different set when the
 * same documents are split over two segments.
 */
public class TestDeleteByQueryMatchesSearchHits extends LuceneTestCase {

  private static Document doc(int id) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    return doc;
  }

  private static TreeSet<Integer> liveIds(IndexReader reader) throws IOException {
    TreeSet<Integer> ids = new TreeSet<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      Bits liveDocs = ctx.reader().getLiveDocs();
      StoredFields storedFields = ctx.reader().storedFields();
      for (int doc = 0; doc < ctx.reader().maxDoc(); doc++) {
        if (liveDocs == null || liveDocs.get(doc)) {
          ids.add(storedFields.document(doc).getField("id").numericValue().intValue());
        }
      }
    }
    return ids;
  }

  /** One segment per inner list; then search, delete (or update) with the query, and compare. */
  private static void assertDeletesSearchHits(
      List<List<Document>> segments, Query query, boolean viaUpdate) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE); // only to pin the segment layout
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (List<Document> segment : segments) {
        for (Document doc : segment) {
          w.addDocument(doc);
        }
        w.commit();
      }
      TreeSet<Integer> searched = new TreeSet<>();
      TreeSet<Integer> before;
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(segments.size(), reader.leaves().size());
        IndexSearcher searcher = new IndexSearcher(reader);
        StoredFields storedFields = searcher.storedFields();
        for (ScoreDoc sd : searcher.search(query, reader.maxDoc()).scoreDocs) {
          searched.add(storedFields.document(sd.doc).getField("id").numericValue().intValue());
        }
        before = liveIds(reader);
      }
      if (viaUpdate) {
        w.updateDocuments(query, List.of(doc(-1)));
      } else {
        w.deleteDocuments(query);
      }
      w.commit();
      TreeSet<Integer> deleted = new TreeSet<>(before);
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        deleted.removeAll(liveIds(reader));
      }
      assertEquals(
          (viaUpdate ? "updateDocuments(" : "deleteDocuments(")
              + query
              + ") on "
              + segments.size()
              + " segment(s) must delete the documents search returns",
          searched,
          deleted);
    }
  }

  /** Control, a per-document query: ids 0 and 3 are red, in two segments. Passes. */
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
    assertDeletesSearchHits(segments, new TermQuery(new Term("color", "red")), false);
  }

  /** Six documents with vectors (id + 1, 1); the 2 nearest to (0, 1) are ids 0 and 1. */
  private static List<List<Document>> knnDocs(int numSegments) {
    List<List<Document>> segments = new ArrayList<>();
    for (int s = 0; s < numSegments; s++) {
      segments.add(new ArrayList<>());
    }
    for (int id = 0; id < 6; id++) {
      Document doc = doc(id);
      doc.add(
          new KnnFloatVectorField(
              "vector", new float[] {id + 1, 1f}, VectorSimilarityFunction.EUCLIDEAN));
      segments.get(id * numSegments / 6).add(doc); // 2 segments: ids 0-2 and ids 3-5
    }
    return segments;
  }

  private static Query knnQuery() {
    return new KnnFloatVectorQuery("vector", new float[] {0f, 1f}, 2);
  }

  /** Control: the same six documents in one segment. Passes: deletes ids 0 and 1. */
  public void testKnnOneSegmentControl() throws IOException {
    assertDeletesSearchHits(knnDocs(1), knnQuery(), false);
  }

  /** Two segments: search returns [0, 1], the delete removes the top 2 of each segment. */
  public void testKnnTwoSegments() throws IOException {
    assertDeletesSearchHits(knnDocs(2), knnQuery(), false);
  }

  /** The same through updateDocuments(Query, docs). */
  public void testKnnTwoSegmentsUpdateDocuments() throws IOException {
    assertDeletesSearchHits(knnDocs(2), knnQuery(), true);
  }

  /** Distinct variants of "lucenex" with one substitution (edits = 1) or two (edits = 2). */
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
   * Default FuzzyQuery (maxExpansions = 50). Segment 1: 50 names one edit from "lucenex" (ids
   * 0-49); segment 2: 10 names two edits away (ids 50-59). Search keeps the 50 best terms of the
   * index and returns ids 0-49; each segment keeps its own 50 best terms, so the delete also
   * removes ids 50-59.
   */
  public void testDefaultFuzzyQuery() throws IOException {
    List<List<Document>> segments = new ArrayList<>();
    int id = 0;
    for (List<String> names : List.of(variants(1, 50), variants(2, 10))) {
      List<Document> segment = new ArrayList<>();
      for (String name : names) {
        Document doc = doc(id++);
        doc.add(new StringField("name", name, Field.Store.NO));
        segment.add(doc);
      }
      segments.add(segment);
    }
    assertEquals(60, id);
    assertDeletesSearchHits(segments, new FuzzyQuery(new Term("name", "lucenex")), false);
  }
}
