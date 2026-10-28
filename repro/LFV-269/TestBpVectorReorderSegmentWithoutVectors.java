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
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * BPReorderingMergePolicy with a BpVectorReorderer merges a segment with vectors and a segment in
 * which no document has the (optional) vector field. The merge must succeed and keep every vector
 * with its document, as it does when both segments have vectors.
 */
public class TestBpVectorReorderSegmentWithoutVectors extends LuceneTestCase {

  private static final String FIELD = "vector";

  private static float[] vector(int id) {
    return new float[] {id % 7, 1 + id % 3};
  }

  /** Adds documents {@code from..to-1} and commits, so that they form one segment. */
  private static void addSegment(IndexWriter w, int from, int to, boolean withVectors)
      throws IOException {
    for (int id = from; id < to; id++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(id), Field.Store.YES));
      if (withVectors) {
        doc.add(new KnnFloatVectorField(FIELD, vector(id)));
      }
      w.addDocument(doc);
    }
    w.commit();
  }

  private void mergeAndCheck(boolean secondSegmentHasVectors) throws IOException {
    BPReorderingMergePolicy mp =
        new BPReorderingMergePolicy(new LogDocMergePolicy(), new BpVectorReorderer(FIELD));
    IndexWriterConfig iwc =
        new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()).setMergePolicy(mp);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      addSegment(w, 0, 100, true); // 100 vectors: enough to partition (2 x minPartitionSize 32)
      addSegment(w, 100, 110, secondSegmentHasVectors); // 10 documents
      w.forceMerge(1);
      assertTrue(w.isOpen());

      try (DirectoryReader r = DirectoryReader.open(w)) {
        assertEquals(1, r.leaves().size());
        LeafReader leaf = r.leaves().get(0).reader();
        assertEquals(110, leaf.numDocs());
        // the reorderer really ran on this merge
        assertEquals(
            "true",
            ((SegmentReader) leaf)
                .getSegmentInfo()
                .info
                .getDiagnostics()
                .get(BPReorderingMergePolicy.REORDERED));
        FloatVectorValues values = leaf.getFloatVectorValues(FIELD);
        assertEquals(secondSegmentHasVectors ? 110 : 100, values.size());
        // every vector still belongs to its document
        StoredFields storedFields = leaf.storedFields();
        KnnVectorValues.DocIndexIterator it = values.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          int id = Integer.parseInt(storedFields.document(doc).get("id"));
          assertTrue(secondSegmentHasVectors || id < 100);
          assertArrayEquals(vector(id), values.vectorValue(it.index()), 0f);
        }
      }
    }
  }

  /** Control: both segments have vectors. */
  public void testBothSegmentsHaveVectors() throws IOException {
    mergeAndCheck(true);
  }

  /** The second segment has no document with the vector field. */
  public void testSegmentWithoutVectors() throws IOException {
    mergeAndCheck(false);
  }
}
