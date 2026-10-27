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
package org.apache.lucene.util.bkd;

import java.io.IOException;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PointsFormat;
import org.apache.lucene.codecs.PointsReader;
import org.apache.lucene.codecs.PointsWriter;
import org.apache.lucene.codecs.lucene90.Lucene90PointsReader;
import org.apache.lucene.codecs.lucene90.Lucene90PointsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * BKDWriter sizes its on-heap point buffer from maxMBSortInHeap without capping it at the largest
 * Java array: HeapPointWriter allocates {@code new byte[bytesPerDoc * size]} in int arithmetic.
 * With maxMBSortInHeap above 2048 MB, a writer that is asked for more than 2^31 - 1 bytes of points
 * (totalPointCount * bytesPerDoc) throws NegativeArraySizeException instead of spilling to disk.
 * Every caller that writes points with BKDWriter#add, for example every merge of a field with more
 * than one dimension, is affected.
 */
public class TestBKDHeapSortBufferOverflow extends LuceneTestCase {

  /** BKDWriter directly: one point, an upper bound of 8.4M points, a 2200 MB sort buffer. */
  public void testAddWithLargeSortBufferAnd256BytePoints() throws IOException {
    // 16 dimensions (8 indexed) of 16 bytes: bytesPerDoc = 256 + 4 = 260.
    // 2200 MB allows 8,872,566 points on heap; 8,400,000 * 260 = 2,184,000,000 > Integer.MAX_VALUE.
    assertAddWorks(16, 8, 16, 2200.0, 8_400_000L);
  }

  /** The same with the shape of LatLonPoint (2 dims of 4 bytes) and 200M points. */
  public void testAddWithLargeSortBufferAndTwoDimIntPoints() throws IOException {
    // bytesPerDoc = 12; 4000 MB allows 349,525,333 points on heap; 200M * 12 = 2.4e9.
    assertAddWorks(2, 2, 4, 4000.0, 200_000_000L);
  }

  /** Control: a sort buffer below 2048 MB makes the writer spill to disk, and the same add works. */
  public void testControlSmallerSortBufferSpillsToDisk() throws IOException {
    assertAddWorks(16, 8, 16, 2000.0, 8_400_000L);
  }

  private void assertAddWorks(
      int numDims, int numIndexDims, int bytesPerDim, double maxMBSortInHeap, long totalPointCount)
      throws IOException {
    BKDConfig config = new BKDConfig(numDims, numIndexDims, bytesPerDim, 512);
    int packedBytesLength = numDims * bytesPerDim;
    try (Directory dir = new ByteBuffersDirectory();
        BKDWriter w = new BKDWriter(1, dir, "tmp", config, maxMBSortInHeap, totalPointCount)) {
      try {
        // totalPointCount is "an upper bound on how many points the caller will add (includes
        // deletions)", so adding a single point is legal.
        w.add(new byte[packedBytesLength], 0);
      } catch (NegativeArraySizeException | ArrayIndexOutOfBoundsException e) {
        throw new AssertionError(
            "BKDWriter.add failed with maxMBSortInHeap="
                + maxMBSortInHeap
                + " totalPointCount="
                + totalPointCount
                + " bytesPerDoc="
                + (packedBytesLength + Integer.BYTES),
            e);
      }
      try (IndexOutput meta = dir.createOutput("m", IOContext.DEFAULT);
          IndexOutput index = dir.createOutput("i", IOContext.DEFAULT);
          IndexOutput data = dir.createOutput("d", IOContext.DEFAULT)) {
        w.finish(meta, index, data).run();
      }
    }
  }

  /**
   * End to end through IndexWriter: a custom PointsFormat over the public Lucene90PointsWriter
   * constructor with maxMBSortInHeap = 2200, 8.4M points of 256 bytes in five flushed segments, then
   * forceMerge(1). Needs about 3 GB of heap (the indexing buffer) and runs for under a minute; a fixed
   * writer spills the 2.2 GB of points to temporary files instead (about 5 GB of disk at the peak).
   */
  public void testForceMergeWithLargeSortBuffer() throws IOException {
    final int docs = 84_000;
    final int pointsPerDoc = 100;
    FieldType ft = new FieldType();
    ft.setDimensions(16, 8, 16);
    ft.freeze();
    Codec def = Codec.getDefault();
    Codec codec =
        new FilterCodec(def.getName(), def) {
          @Override
          public PointsFormat pointsFormat() {
            return new PointsFormat() {
              @Override
              public PointsWriter fieldsWriter(SegmentWriteState state) throws IOException {
                return new Lucene90PointsWriter(state, 512, 2200.0);
              }

              @Override
              public PointsReader fieldsReader(SegmentReadState state) throws IOException {
                return new Lucene90PointsReader(state);
              }
            };
          }
        };
    // on disk, so that a writer that (correctly) spills 2 GiB of points to temp files does not need the heap
    try (Directory dir = FSDirectory.open(createTempDir("h182"))) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(codec);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      iwc.setMergeScheduler(new SerialMergeScheduler());
      iwc.setMaxBufferedDocs(docs / 5);
      iwc.setRAMBufferSizeMB(IndexWriterConfig.DISABLE_AUTO_FLUSH);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Field point = new Field("p", new byte[256], ft);
        Document doc = new Document();
        for (int i = 0; i < pointsPerDoc; i++) {
          doc.add(point);
        }
        for (int i = 0; i < docs; i++) {
          w.addDocument(doc);
        }
        w.commit();
        w.getConfig().setMergePolicy(new LogDocMergePolicy());
        try {
          w.forceMerge(1);
        } catch (Throwable t) {
          throw new AssertionError(
              "forceMerge(1) of "
                  + (long) docs * pointsPerDoc
                  + " points failed; writer open="
                  + w.isOpen()
                  + ", tragic exception="
                  + w.getTragicException(),
              t);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        assertEquals((long) docs * pointsPerDoc, PointValues.size(r, "p"));
      }
    }
  }
}
