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
import java.io.InputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.hnsw.HnswGraphProvider;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.Version;
import org.apache.lucene.util.hnsw.HnswGraph;

/**
 * The zipped indexes were written by the Lucene 9.12.0, 10.0.0 and 10.1.0 release jars with their
 * default codec and only public API (fixture-src/DupGen3.java: 1000 one-dimensional vectors, commits
 * at random points, TieredMergePolicy, SerialMergeScheduler). Their merged segments contain HNSW
 * neighbour lists that hold the same neighbour twice, which those releases' graph builders produce
 * (apache/lucene#14127; deduplicated at write time only since #14157 in 10.2).
 *
 * <p>The indexes are valid and fully usable on main: they open, every vector is readable and kNN
 * search works. CheckIndex should therefore accept them; on main its HNSW graph check (#13984)
 * throws "has repeated neighbors" for them (with failFast; without failFast it prints the same
 * ERROR, see the companion candidate about the clean flag).
 */
public class TestCheckIndexOldGraphRepeatedNeighbors extends LuceneTestCase {

  private Path unzip(String version) throws Exception {
    Path dir = createTempDir("repeated-neighbors-" + version);
    try (InputStream in = getClass().getResourceAsStream("repeated-neighbors." + version + ".zip")) {
      assertNotNull("fixture for " + version, in);
      TestUtil.unzip(in, dir);
    }
    return dir;
  }

  /**
   * The per-field format's reader for {@code field}: public API that is named
   * KnnVectorsReader#unwrapReaderForField on main and PerFieldKnnVectorsFormat.FieldsReader
   * #getFieldReader in 10.x (looked up by name so the same test also compiles on releases).
   */
  private static KnnVectorsReader fieldReader(KnnVectorsReader reader, String field)
      throws Exception {
    try {
      return (KnnVectorsReader)
          KnnVectorsReader.class.getMethod("unwrapReaderForField", String.class).invoke(reader, field);
    } catch (NoSuchMethodException e) {
      return (KnnVectorsReader)
          reader.getClass().getMethod("getFieldReader", String.class).invoke(reader, field);
    }
  }

  private void checkRelease(String version) throws Exception {
    Path path = unzip(version);
    try (Directory dir = FSDirectory.open(path)) {
      // The index was written by the stated release and is usable on main.
      SegmentInfos infos = SegmentInfos.readLatestCommit(dir);
      int repeated = 0;
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1000, reader.numDocs());
        for (LeafReaderContext ctx : reader.leaves()) {
          CodecReader cr = (CodecReader) ctx.reader();
          assertEquals(Version.parse(version), ((SegmentReader) cr).getSegmentInfo().info.getVersion());
          FloatVectorValues values = cr.getFloatVectorValues("vector");
          assertEquals(cr.maxDoc(), values.size());
          KnnVectorsReader kvr = fieldReader(cr.getVectorReader(), "vector");
          HnswGraph graph = ((HnswGraphProvider) kvr).getGraph("vector");
          for (int level = 0; level < graph.numLevels(); level++) {
            HnswGraph.NodesIterator nodes = graph.getNodesOnLevel(level);
            while (nodes.hasNext()) {
              graph.seek(level, nodes.nextInt());
              int prev = -1;
              for (int n = graph.nextNeighbor(); n != Integer.MAX_VALUE; n = graph.nextNeighbor()) {
                if (n == prev) {
                  repeated++;
                }
                prev = n;
              }
            }
          }
        }
        TopDocs td = new IndexSearcher(reader).search(new KnnFloatVectorQuery("vector", new float[] {3}, 5), 5);
        assertEquals(5, td.scoreDocs.length);
      }
      assertTrue("fixture should contain a graph with a repeated neighbour", repeated > 0);
      assertTrue(infos.size() >= 1);

      // CheckIndex must accept a valid index written by a supported release.
      ByteArrayOutputStream log = new ByteArrayOutputStream();
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_INTEGRITY_CHECKS);
        checker.setFailFast(true);
        checker.setInfoStream(new PrintStream(log, true, StandardCharsets.UTF_8));
        CheckIndex.Status status;
        try {
          status = checker.checkIndex();
        } catch (CheckIndex.CheckIndexException e) {
          throw new AssertionError(
              "CheckIndex rejects a valid index written by Lucene " + version + ": " + e.getMessage(), e);
        }
        assertTrue(status.clean);
      }
    }
  }

  public void testWrittenBy10_1_0() throws Exception {
    checkRelease("10.1.0");
  }

  public void testWrittenBy10_0_0() throws Exception {
    checkRelease("10.0.0");
  }

  public void testWrittenBy9_12_0() throws Exception {
    checkRelease("9.12.0");
  }
}
