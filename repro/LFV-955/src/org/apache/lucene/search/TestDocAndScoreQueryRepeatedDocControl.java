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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * CONTROL for TestDocAndScoreQueryRepeatedDoc: identical scenarios, but each document is kept once
 * (its first, highest-scoring entry) before the factory is called; expected to pass on main.
 *
 * <p>DocAndScoreQuery.createDocAndScoreQuery (public since #16686, for custom kNN queries) is
 * documented to "match exactly the documents in topK". A custom kNN query over two vector fields
 * that concatenates the two result lists and sorts them by descending score (the documented order
 * precondition) passes a document found through both fields twice. Uses only public API (the
 * test sits in org.apache.lucene.search like Lucene's own tests, but calls nothing
 * package-private).
 */
public class TestDocAndScoreQueryRepeatedDocControl extends LuceneTestCase {

  /** Two kNN searches, one per field; both lists in one TopDocs, sorted by descending score. */
  private static TopDocs twoFieldKnn(IndexSearcher s, float[] qa, float[] qb, int k)
      throws IOException {
    TopDocs ta = s.search(new KnnFloatVectorQuery("a", qa, k), k);
    TopDocs tb = s.search(new KnnFloatVectorQuery("b", qb, k), k);
    ScoreDoc[] all = new ScoreDoc[ta.scoreDocs.length + tb.scoreDocs.length];
    System.arraycopy(ta.scoreDocs, 0, all, 0, ta.scoreDocs.length);
    System.arraycopy(tb.scoreDocs, 0, all, ta.scoreDocs.length, tb.scoreDocs.length);
    Arrays.sort(all, (x, y) -> Float.compare(y.score, x.score));
    return new TopDocs(new TotalHits(all.length, TotalHits.Relation.EQUAL_TO), all);
  }

  private static Directory index(Directory dir, int[] segmentSizes, boolean deleteDoc1)
      throws IOException {
    IndexWriterConfig iwc =
        new IndexWriterConfig()
            .setCodec(TestUtil.getDefaultCodec())
            .setMergePolicy(NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      int id = 0;
      for (int size : segmentSizes) {
        for (int i = 0; i < size; i++, id++) {
          Document d = new Document();
          d.add(new StringField("id", Integer.toString(id), Field.Store.NO));
          d.add(new NumericDocValuesField("v", 1000 + id));
          d.add(new KnnFloatVectorField("a", new float[] {id, 1}));
          d.add(new KnnFloatVectorField("b", new float[] {1, id}));
          w.addDocument(d);
        }
        w.flush();
      }
      if (deleteDoc1) {
        w.deleteDocuments(new Term("id", "1"));
      }
      w.commit();
    }
    return dir;
  }

  private static TopDocs keepFirstPerDoc(TopDocs td) {
    java.util.Set<Integer> seen = new java.util.HashSet<>();
    List<ScoreDoc> out = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) if (seen.add(sd.doc)) out.add(sd);
    return new TopDocs(td.totalHits, out.toArray(new ScoreDoc[0]));
  }

  private static List<Integer> docs(TopDocs td) {
    List<Integer> l = new ArrayList<>();
    for (ScoreDoc sd : td.scoreDocs) l.add(sd.doc);
    return l;
  }

  /** Every repeated entry is counted, returned and iterated twice. */
  public void testDocumentFoundThroughBothFieldsIsCountedOnce() throws IOException {
    try (Directory dir = index(newDirectory(), new int[] {5, 5}, false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      TopDocs topK = twoFieldKnn(s, new float[] {3, 1}, new float[] {1, 3}, 4);
      TreeSet<Integer> distinct = new TreeSet<>(docs(topK));
      assertEquals("both fields find the same 4 documents", 4, distinct.size());
      assertEquals(8, topK.scoreDocs.length);

      topK = keepFirstPerDoc(topK);
      Query q = DocAndScoreQuery.createDocAndScoreQuery(r, topK, 0);

      List<Integer> iterated = new ArrayList<>();
      Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE, 1f);
      int weightCount = 0;
      for (LeafReaderContext ctx : r.leaves()) {
        weightCount += w.count(ctx);
        Scorer sc = w.scorer(ctx);
        if (sc == null) continue;
        DocIdSetIterator it = sc.iterator();
        for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
          iterated.add(ctx.docBase + d);
        }
      }
      TopDocs hits = s.search(q, 100);
      String state =
          "IndexSearcher#count="
              + s.count(q)
              + " Weight#count="
              + weightCount
              + " iterated="
              + iterated
              + " hits="
              + docs(hits)
              + " totalHits="
              + hits.totalHits;
      // the query matches exactly the 4 documents of topK
      assertEquals(state, distinct.size(), s.count(q));
      assertEquals(state, distinct.size(), weightCount);
      assertEquals(state, new ArrayList<>(distinct), iterated); // strictly increasing, once each
      assertEquals(state, distinct.size(), hits.scoreDocs.length);
      assertEquals(state, distinct.size(), hits.totalHits.value());
    }
  }

  /**
   * When the repeated document is the first document of a segment, the previous segment's
   * iterator returns a doc id equal to that segment's maxDoc.
   */
  public void testRepeatedFirstDocOfSegmentStaysInItsSegment() throws IOException {
    try (Directory dir = index(newDirectory(), new int[] {64, 3}, false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      // nearest documents in both fields: 64 (the first doc of segment 1), then 65
      TopDocs topK = twoFieldKnn(s, new float[] {64.4f, 1}, new float[] {1, 64.4f}, 2);
      assertEquals(List.of(64, 64, 65, 65), docs(topK));
      topK = keepFirstPerDoc(topK);
      Query q = DocAndScoreQuery.createDocAndScoreQuery(r, topK, 0);

      LeafReaderContext seg0 = r.leaves().get(0);
      Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
      Scorer sc = w.scorer(seg0);
      List<Integer> seg0Docs = new ArrayList<>();
      if (sc != null) {
        DocIdSetIterator it = sc.iterator();
        for (int d = it.nextDoc(); d != DocIdSetIterator.NO_MORE_DOCS; d = it.nextDoc()) {
          seg0Docs.add(d);
        }
      }
      assertEquals(
          "segment 0 (maxDoc " + seg0.reader().maxDoc() + ") holds none of docs 64, 65",
          List.of(),
          seg0Docs);
    }
  }

  /** Same shape, hits sorted by a doc-values field: the sort reads segment 0 at doc 64. */
  public void testRepeatedFirstDocOfSegmentSortedByDocValues() throws IOException {
    try (Directory dir = index(newDirectory(), new int[] {64, 3}, false);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      TopDocs topK = twoFieldKnn(s, new float[] {64.4f, 1}, new float[] {1, 64.4f}, 2);
      assertEquals(List.of(64, 64, 65, 65), docs(topK));
      topK = keepFirstPerDoc(topK);
      Query q = DocAndScoreQuery.createDocAndScoreQuery(r, topK, 0);
      TopDocs sorted = s.search(q, 10, new Sort(new SortField("v", SortField.Type.LONG)));
      assertEquals(List.of(64, 65), docs(sorted));
    }
  }

  /** Same shape on a segment with a deletion: a plain search fails. */
  public void testRepeatedFirstDocOfSegmentWithDeletions() throws IOException {
    try (Directory dir = index(newDirectory(), new int[] {64, 3}, true);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = new IndexSearcher(r);
      s.setQueryCache(null);
      TopDocs topK = twoFieldKnn(s, new float[] {64.4f, 1}, new float[] {1, 64.4f}, 2);
      assertEquals(List.of(64, 64, 65, 65), docs(topK));
      topK = keepFirstPerDoc(topK);
      Query q = DocAndScoreQuery.createDocAndScoreQuery(r, topK, 0);
      TopDocs hits = s.search(q, 10);
      assertEquals(List.of(64, 65), docs(hits));
    }
  }
}
