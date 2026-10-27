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

import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MergePolicy;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.SortingCodecReader;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexWriter#addIndexes(CodecReader...) of a SortingCodecReader (the reader's documented use:
 * "should only be used for merging") into a writer whose merge policy is
 * BPReorderingMergePolicy(BpVectorReorderer) with default settings. BPReorderingMergePolicy treats
 * addIndexes as a forced merge and reorders it; BpVectorReorderer copies the merged view's vector
 * values, which copies the SortingCodecReader's vector values, whose copy() throws
 * UnsupportedOperationException. addIndexes must succeed and every document keep its vector.
 */
public class TestAddIndexesSortingCodecReaderBpVectorReorder extends LuceneTestCase {

  static final int NUM_DOCS = 200; // > 2 * default min partition size, so BP really partitions

  static float[] vector(int id) {
    return new float[] {(id % 13) + 1, (id % 7) - 3, id % 5, 1};
  }

  private static Directory sourceIndex(int numSegments) throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc =
        new IndexWriterConfig().setMergePolicy(org.apache.lucene.index.NoMergePolicy.INSTANCE);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int id = 0; id < NUM_DOCS; id++) {
        if (numSegments == 2 && id == NUM_DOCS / 2) {
          w.flush();
        }
        Document doc = new Document();
        doc.add(new NumericDocValuesField("id", id));
        doc.add(new NumericDocValuesField("rev", -id));
        doc.add(new KnnFloatVectorField("v", vector(id), VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  /** Every document in dir must have vector(id). */
  private static void assertVectorsKept(Directory dir) throws Exception {
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      assertEquals(NUM_DOCS, r.numDocs());
      int seen = 0;
      for (LeafReaderContext ctx : r.leaves()) {
        LeafReader leaf = ctx.reader();
        NumericDocValues ids = leaf.getNumericDocValues("id");
        FloatVectorValues values = leaf.getFloatVectorValues("v");
        KnnVectorValues.DocIndexIterator it = values.iterator();
        for (int doc = it.nextDoc();
            doc != KnnVectorValues.DocIndexIterator.NO_MORE_DOCS;
            doc = it.nextDoc()) {
          assertTrue(ids.advanceExact(doc));
          int id = (int) ids.longValue();
          assertArrayEquals("id " + id, vector(id), values.vectorValue(it.index()), 0f);
          seen++;
        }
      }
      assertEquals(NUM_DOCS, seen);
    }
  }

  private static void addIndexes(
      MergePolicy targetPolicy, boolean wrapWithSortingReader, int numSegments) throws Exception {
    try (Directory source = sourceIndex(numSegments);
        DirectoryReader sourceReader = DirectoryReader.open(source);
        Directory target = newDirectory()) {
      assertEquals(numSegments, sourceReader.leaves().size());
      CodecReader[] in = new CodecReader[numSegments];
      for (int i = 0; i < numSegments; i++) {
        CodecReader leaf = (CodecReader) sourceReader.leaves().get(i).reader();
        in[i] =
            wrapWithSortingReader
                ? SortingCodecReader.wrap(leaf, new Sort(new SortField("rev", SortField.Type.LONG)))
                : leaf;
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(targetPolicy)
              .setMergeScheduler(new SerialMergeScheduler());
      try (IndexWriter w = new IndexWriter(target, iwc)) {
        w.addIndexes(in);
        w.commit();
      }
      assertVectorsKept(target);
    }
  }

  private static MergePolicy bpVectorPolicy() {
    return new BPReorderingMergePolicy(new TieredMergePolicy(), new BpVectorReorderer("v"));
  }

  /** One SortingCodecReader: BpVectorReorderer copies its vector values directly. */
  public void testSortingCodecReaderIntoBpVectorReorderingWriter() throws Exception {
    addIndexes(bpVectorPolicy(), true, 1);
  }

  /** Two SortingCodecReaders in one call: the copy goes through the merged view's sub copies. */
  public void testTwoSortingCodecReadersIntoBpVectorReorderingWriter() throws Exception {
    addIndexes(bpVectorPolicy(), true, 2);
  }

  /** Control: same SortingCodecReader inputs, no reordering merge policy. */
  public void testControlSortingCodecReadersNoReordering() throws Exception {
    addIndexes(new TieredMergePolicy(), true, 2);
  }

  /** Control: plain segment readers into the reordering writer. */
  public void testControlPlainReadersIntoBpVectorReorderingWriter() throws Exception {
    addIndexes(bpVectorPolicy(), false, 2);
  }
}
