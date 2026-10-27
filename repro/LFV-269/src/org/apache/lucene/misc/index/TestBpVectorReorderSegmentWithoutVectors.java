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
package org.apache.lucene.misc.index;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BPReorderingMergePolicy with a BpVectorReorderer cannot merge a segment that has no vectors for
 * the reordering field (for example a flush of documents that do not carry the optional vector)
 * with segments that have vectors: the merged view's FloatVectorValues.copy() throws
 * NullPointerException, so the merge fails, and it fails again every time it is retried. Only
 * public API and default reorderer settings are used.
 */
public class TestBpVectorReorderSegmentWithoutVectors extends LuceneTestCase {

  private static void addDocs(IndexWriter w, int count, boolean withVector) throws IOException {
    for (int i = 0; i < count; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", (withVector ? "v" : "n") + i, Field.Store.YES));
      if (withVector) {
        doc.add(new KnnFloatVectorField("vector", new float[] {i % 7, 1 + i % 3}));
      }
      w.addDocument(doc);
    }
    w.commit(); // one segment per call (the merge policy below never merges on its own)
  }

  private static void mergeAndCheck(boolean secondSegmentHasVectors) throws IOException {
    BPReorderingMergePolicy mp =
        new BPReorderingMergePolicy(new LogDocMergePolicy(), new BpVectorReorderer("vector"));
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(mp))) {
        addDocs(w, 100, true);
        addDocs(w, 10, secondSegmentHasVectors);
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals(110, r.numDocs());
        int expectedVectors = secondSegmentHasVectors ? 110 : 100;
        assertEquals(
            expectedVectors, r.leaves().get(0).reader().getFloatVectorValues("vector").size());
      }
    }
  }

  /** Control: every segment has vectors for the field; the reordered merge succeeds. */
  public void testControlAllSegmentsHaveVectors() throws IOException {
    mergeAndCheck(true);
  }

  /** One of the merged segments has no document with the vector field. */
  public void testSegmentWithoutVectors() throws IOException {
    mergeAndCheck(false);
  }
}
