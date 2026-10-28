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
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Bits;

/**
 * Single-segment index, default configuration apart from NoMergePolicy. A KnnFloatVectorQuery
 * (k=1) passed to IndexWriter#deleteDocuments(Query...) should delete the nearest live document at
 * the time of the call, which is what IndexSearcher#search returns on a reader opened at that
 * point. The writer instead runs the query against a segment reader whose live docs do not
 * include the documents this writer already deleted, and (for documents still in the RAM buffer)
 * that does include documents added after the call.
 */
public class TestKnnDeleteQueryLiveDocsView extends LuceneTestCase {

  private static final float[] TARGET = {0f, 1f};

  private static IndexWriterConfig config() {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    return iwc;
  }

  /** Document i has vector (i + 1, 1): document 0 is the nearest to TARGET, then 1, 2, ... */
  private static Document doc(int id, float x) {
    Document doc = new Document();
    doc.add(new StoredField("id", id));
    doc.add(new StringField("sid", "d" + id, Field.Store.NO));
    doc.add(new KnnFloatVectorField("vector", new float[] {x, 1f}, VectorSimilarityFunction.EUCLIDEAN));
    return doc;
  }

  private static Directory buildOneSegment(int numDocs) throws IOException {
    Directory dir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(dir, config())) {
      for (int i = 0; i < numDocs; i++) {
        w.addDocument(doc(i, i + 1));
      }
      w.commit();
    }
    return dir;
  }

  private static TreeSet<Integer> ids(IndexSearcher searcher, ScoreDoc[] hits) throws IOException {
    TreeSet<Integer> ids = new TreeSet<>();
    StoredFields storedFields = searcher.storedFields();
    for (ScoreDoc sd : hits) {
      ids.add(storedFields.document(sd.doc).getField("id").numericValue().intValue());
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

  private static TreeSet<Integer> deleted(Directory dir, int numDocs) throws IOException {
    TreeSet<Integer> live = liveIds(dir);
    TreeSet<Integer> deleted = new TreeSet<>();
    for (int i = 0; i < numDocs; i++) {
      if (live.contains(i) == false) {
        deleted.add(i);
      }
    }
    return deleted;
  }

  private static Query knn1() {
    return new KnnFloatVectorQuery("vector", TARGET, 1);
  }

  /** Search agrees with the expectation used below: after deleting d0 the nearest live doc is 1. */
  public void testSearchAtTheDeletePoint() throws IOException {
    try (Directory dir = buildOneSegment(10);
        IndexWriter w = new IndexWriter(dir, config())) {
      w.deleteDocuments(new Term("sid", "d0"));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        assertEquals(Set.of(1), ids(searcher, searcher.search(knn1(), 1).scoreDocs));
      }
      w.rollback();
    }
  }

  /** Delete d0, then the nearest document: documents 0 and 1 must be gone. */
  public void testKnnDeleteAfterTermDelete() throws IOException {
    try (Directory dir = buildOneSegment(10)) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.deleteDocuments(new Term("sid", "d0"));
        w.deleteDocuments(knn1());
        w.commit();
      }
      assertEquals(Set.of(0, 1), deleted(dir, 10));
    }
  }

  /** Same, with the first delete committed before the kNN delete is issued. */
  public void testKnnDeleteAfterCommittedDelete() throws IOException {
    try (Directory dir = buildOneSegment(10)) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.deleteDocuments(new Term("sid", "d0"));
        w.commit();
        w.deleteDocuments(knn1());
        w.commit();
      }
      assertEquals(Set.of(0, 1), deleted(dir, 10));
    }
  }

  /** Two "delete the nearest document" calls with an NRT reopen in between delete two documents. */
  public void testKnnDeleteTwice() throws IOException {
    try (Directory dir = buildOneSegment(10)) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        w.deleteDocuments(knn1());
        DirectoryReader.open(w).close();
        w.deleteDocuments(knn1());
        w.commit();
      }
      assertEquals(Set.of(0, 1), deleted(dir, 10));
    }
  }

  /**
   * Documents still in the RAM buffer: the kNN delete must not be influenced by a document added
   * after the call (sequence numbers order the operations; the later document is not deleted, and
   * it must not shield document 0).
   */
  public void testKnnDeleteIgnoresDocumentAddedLater() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, config())) {
        for (int i = 0; i < 10; i++) {
          w.addDocument(doc(i, i + 1));
        }
        w.deleteDocuments(knn1());
        w.addDocument(doc(100, 0.5f)); // nearer to TARGET than document 0
        w.commit();
      }
      assertTrue("the later document must stay", liveIds(dir).contains(100));
      assertEquals(Set.of(0), deleted(dir, 10));
    }
  }
}
