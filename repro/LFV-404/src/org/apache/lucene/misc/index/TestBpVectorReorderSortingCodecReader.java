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
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.CodecReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.Sorter;
import org.apache.lucene.index.SortingCodecReader;
import org.apache.lucene.index.TieredMergePolicy;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBpVectorReorderSortingCodecReader extends LuceneTestCase {

  public void testAddIndexesOfSortingCodecReader() throws IOException {
    try (Directory source = new ByteBuffersDirectory();
        Directory target = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(source, new IndexWriterConfig())) {
        for (int i = 0; i < 64; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1}));
          w.addDocument(doc);
        }
      }
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setMergePolicy(
                  new BPReorderingMergePolicy(new TieredMergePolicy(), new BpVectorReorderer("v")))
              .setMergeScheduler(new SerialMergeScheduler());
      try (DirectoryReader reader = DirectoryReader.open(source);
          IndexWriter w = new IndexWriter(target, iwc)) {
        Sorter.DocMap reversed =
            new Sorter.DocMap() {
              @Override
              public int oldToNew(int docID) {
                return 63 - docID;
              }

              @Override
              public int newToOld(int docID) {
                return 63 - docID;
              }

              @Override
              public int size() {
                return 64;
              }
            };
        CodecReader leaf = (CodecReader) getOnlyLeafReader(reader);
        try {
          w.addIndexes(SortingCodecReader.wrap(leaf, reversed, null));
        } catch (UnsupportedOperationException e) {
          throw new AssertionError(
              "expected addIndexes to add (and reorder) the 64 documents, got " + e, e);
        }
      }
    }
  }

  /**
   * The doc map that BpVectorReorderer computes for a SortingCodecReader has to be in that reader's
   * doc space: the reorderer's ordinal permutation says where each vector ordinal goes, and a
   * document has to go to the place of its own vector's ordinal (the iterator's index() at that
   * document). The reader hands out the 64 documents in a shuffled order.
   */
  public void testDocMapOfSortingCodecReader() throws IOException {
    try (Directory source = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(source, new IndexWriterConfig())) {
        for (int i = 0; i < 64; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("v", new float[] {i, 1}));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(source)) {
        Sorter.DocMap shuffled =
            new Sorter.DocMap() {
              @Override
              public int oldToNew(int docID) {
                return docID * 37 % 64;
              }

              @Override
              public int newToOld(int docID) {
                return docID * 45 % 64; // 37 * 45 = 26 * 64 + 1
              }

              @Override
              public int size() {
                return 64;
              }
            };
        CodecReader sorted =
            SortingCodecReader.wrap((CodecReader) getOnlyLeafReader(reader), shuffled, null);
        BpVectorReorderer reorderer = new BpVectorReorderer("v");
        FloatVectorValues values = sorted.getFloatVectorValues("v");
        Sorter.DocMap ordMap;
        Sorter.DocMap docMap;
        try {
          ordMap = reorderer.computeValueMap(values, VectorSimilarityFunction.EUCLIDEAN, null);
          docMap = reorderer.computeDocMap(sorted, null, null);
        } catch (UnsupportedOperationException e) {
          throw new AssertionError(
              "expected a doc map for the 64 documents of the SortingCodecReader, got " + e, e);
        }
        int misplaced = 0;
        KnnVectorValues.DocIndexIterator it = values.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          if (docMap.oldToNew(doc) != ordMap.oldToNew(it.index())) {
            misplaced++;
          }
        }
        assertEquals(
            "documents that the doc map does not send to the place of their own vector",
            0,
            misplaced);
      }
    }
  }
}
