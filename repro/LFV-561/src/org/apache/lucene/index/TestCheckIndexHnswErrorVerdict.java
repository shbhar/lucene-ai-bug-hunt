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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.search.AcceptDocs;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.hnsw.HnswGraph;

/**
 * CheckIndex runs an HNSW graph test per segment (GITHUB#13984) that records its failure in {@code
 * SegmentInfoStatus.hnswGraphsStatus.error}, but unlike every other per-segment test that error is
 * never rethrown, so the segment is not counted as broken and {@code Status.clean} stays true
 * ("No problems were detected with this index.") unless failFast is set.
 */
public class TestCheckIndexHnswErrorVerdict extends LuceneTestCase {

  /** Delegates to Lucene99HnswVectorsFormat; its graph view lists node 0's level-0 neighbours in reverse order. */
  public static final class UnorderedNeighborsFormat extends KnnVectorsFormat {
    // tinySegmentsThreshold 0: always build a graph, even for a small test segment
    private final KnnVectorsFormat in = new Lucene99HnswVectorsFormat(16, 100, 0);

    public UnorderedNeighborsFormat() {
      super("UnorderedNeighbors");
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      return in.fieldsWriter(state);
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new Reader(in.fieldsReader(state));
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return in.getMaxDimensions(fieldName);
    }
  }

  static final class Reader extends KnnVectorsReader implements HnswGraphProvider {
    final KnnVectorsReader in;

    Reader(KnnVectorsReader in) {
      this.in = in;
    }

    @Override
    public void checkIntegrity(MergePolicy.OneMerge merge) throws IOException {
      in.checkIntegrity(merge);
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) throws IOException {
      return in.getFloatVectorValues(field);
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) throws IOException {
      return in.getByteVectorValues(field);
    }

    @Override
    public Float16VectorValues getFloat16VectorValues(String field) throws IOException {
      return in.getFloat16VectorValues(field);
    }

    @Override
    public void search(String field, float[] target, KnnCollector c, AcceptDocs a)
        throws IOException {
      in.search(field, target, c, a);
    }

    @Override
    public void search(String field, byte[] target, KnnCollector c, AcceptDocs a)
        throws IOException {
      in.search(field, target, c, a);
    }

    @Override
    public void search(String field, short[] target, KnnCollector c, AcceptDocs a)
        throws IOException {
      in.search(field, target, c, a);
    }

    @Override
    public Map<String, Long> getOffHeapByteSize(FieldInfo fieldInfo) {
      return in.getOffHeapByteSize(fieldInfo);
    }

    @Override
    public HnswGraph getGraph(String field) throws IOException {
      return new ReversedNode0(((HnswGraphProvider) in).getGraph(field));
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }

  /** Same graph, except that node 0's level-0 neighbour list is returned in descending order. */
  static final class ReversedNode0 extends HnswGraph {
    final HnswGraph in;
    int[] reversed;
    int upto = -1;

    ReversedNode0(HnswGraph in) {
      this.in = in;
    }

    @Override
    public void seek(int level, int target) throws IOException {
      in.seek(level, target);
      reversed = null;
      if (level == 0 && target == 0) {
        int[] tmp = new int[in.neighborCount()];
        int n = 0;
        for (int x = in.nextNeighbor(); x != Integer.MAX_VALUE; x = in.nextNeighbor()) tmp[n++] = x;
        reversed = new int[n];
        for (int i = 0; i < n; i++) reversed[i] = tmp[n - 1 - i];
        upto = 0;
      }
    }

    @Override
    public int nextNeighbor() throws IOException {
      if (reversed == null) return in.nextNeighbor();
      return upto < reversed.length ? reversed[upto++] : Integer.MAX_VALUE;
    }

    @Override
    public int size() {
      return in.size();
    }

    @Override
    public int numLevels() throws IOException {
      return in.numLevels();
    }

    @Override
    public int maxConn() {
      return in.maxConn();
    }

    @Override
    public int entryNode() throws IOException {
      return in.entryNode();
    }

    @Override
    public NodesIterator getNodesOnLevel(int level) throws IOException {
      return in.getNodesOnLevel(level);
    }

    @Override
    public int neighborCount() {
      return reversed == null ? in.neighborCount() : reversed.length;
    }
  }

  private static CheckIndex.Status check(Directory dir, ByteArrayOutputStream log)
      throws IOException {
    try (CheckIndex checker = new CheckIndex(dir)) {
      checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
      checker.setInfoStream(new PrintStream(log, true, StandardCharsets.UTF_8));
      return checker.checkIndex();
    }
  }

  private static void assertVerdictReflectsHnswError(CheckIndex.Status status, String log) {
    boolean hnswError = false;
    for (CheckIndex.Status.SegmentInfoStatus seg : status.segmentInfos) {
      if (seg.hnswGraphsStatus != null && seg.hnswGraphsStatus.error != null) {
        hnswError = true;
      }
    }
    assertTrue("test setup: the HNSW graph test should have failed\n" + log, hnswError);
    assertFalse(
        "CheckIndex reports the index clean although its HNSW graph test failed:\n" + log,
        status.clean);
    assertTrue("the segment should be counted as broken", status.numBadSegments > 0);
  }

  /** A graph CheckIndex itself classifies as broken (neighbours out of order). */
  public void testMalformedGraphIsNotReportedClean() throws Exception {
    final KnnVectorsFormat format = new UnorderedNeighborsFormat();
    final Codec def = TestUtil.getDefaultCodec();
    final KnnVectorsFormat perField =
        new PerFieldKnnVectorsFormat() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return format;
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return perField;
          }
        });
    Path path = createTempDir("unordered");
    try (Directory dir = FSDirectory.open(path)) {
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 200; i++) {
          Document doc = new Document();
          doc.add(new KnnFloatVectorField("vector", new float[] {i % 17, i % 5}));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      CheckIndex.Status status = check(dir, log);
      assertVerdictReflectsHnswError(status, log.toString(StandardCharsets.UTF_8));
    }
  }

  /**
   * The same verdict for an index written by the Lucene 10.1.0 release jar (default codec), whose
   * merged graph holds a repeated neighbour (see the companion candidate): CheckIndex prints
   * "ERROR ... has repeated neighbors" and then "No problems were detected with this index."
   */
  public void testErrorOnReleaseWrittenIndexIsNotReportedClean() throws Exception {
    Path path = createTempDir("release-10.1.0");
    try (InputStream in = getClass().getResourceAsStream("repeated-neighbors.10.1.0.zip")) {
      assertNotNull(in);
      TestUtil.unzip(in, path);
    }
    try (Directory dir = FSDirectory.open(path)) {
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      CheckIndex.Status status = check(dir, log);
      assertVerdictReflectsHnswError(status, log.toString(StandardCharsets.UTF_8));
    }
  }
}
