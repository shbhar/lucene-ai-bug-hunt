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
package org.apache.lucene.misc.store;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.LogDocMergePolicy;
import org.apache.lucene.index.SegmentReader;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.DataAccessHint;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.FilterIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.IndexOutput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.junit.BeforeClass;

/**
 * DirectIODirectory "uses DIRECT I/O to bypass OS level IO caching during merging. For all other
 * cases (searching, writing) we delegate to the provided Directory instance." Since #16883/#16905,
 * a vectors or stored-fields reader that is already open (for example by an NRT reader) shares one
 * input between merges and sequential reads by searches whenever that input says it is efficient
 * for the other context (IndexInput#isEfficientFor), and DirectIODirectory's inputs never say they
 * are not. So on an open segment:
 *
 * <ul>
 *   <li>with the default delegate (FSDirectory.open: memory-mapped, default read advice) a merge
 *       never opens the files with a merge context, so DirectIODirectory never uses direct I/O
 *       for it;
 *   <li>with a delegate that advises by context, a merge that comes after a sequential read by a
 *       search reads through the search's input (no direct I/O), and a sequential read by a search
 *       that comes after a merge reads through the merge's direct-I/O input.
 * </ul>
 *
 * The #16905 description says merges open the data file again with IOContext.merge() so that
 * "a directory can tell a merge apart" (its example: DirectIODirectory) and that merges and
 * sequential values share an input "unless the directory serves them differently"; its test
 * TestVectorsMergeReadAdvice#testMergesServedApartOpenOnTheirOwn says "A directory that serves
 * merges apart, as with direct I/O for merges, gets a merge open and a search open, whichever
 * comes first".
 */
public class TestDirectIOMergeInputSharing extends LuceneTestCase {

  private static final int DIM = 32;
  private static final int DOCS_PER_SEGMENT = 200;

  @BeforeClass
  public static void checkSupported() throws IOException {
    assumeTrue(
        "needs a JDK with ExtendedOpenOption.DIRECT",
        DirectIODirectory.ExtendedOpenOption_DIRECT != null);
    Path path = createTempDir("directIOProbe");
    try (DirectIODirectory dir =
            new DirectIODirectory(FSDirectory.open(path)) {
              @Override
              protected boolean useDirectIO(String name, IOContext context, OptionalLong len) {
                return true;
              }
            };
        IndexOutput out = dir.createOutput("out", IOContext.DEFAULT)) {
      out.writeString("test");
    } catch (IOException e) {
      assumeNoException("needs a file system that supports direct I/O", e);
    }
  }

  /** Records the files it serves with direct I/O for reading, and reads of those inputs. */
  static final class RecordingDirectIODirectory extends DirectIODirectory {
    final List<String> directOpens = new CopyOnWriteArrayList<>();
    final AtomicInteger directSlices = new AtomicInteger();

    RecordingDirectIODirectory(FSDirectory delegate) throws IOException {
      // minBytesDirect = 0: every merge read may use direct I/O, whatever the size
      super(delegate, DEFAULT_MERGE_BUFFER_SIZE, 0);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      IndexInput in = super.openInput(name, context);
      if (useDirectIO(name, context, OptionalLong.of(fileLength(name)))) {
        directOpens.add(name);
        return new Counting(in);
      }
      return in;
    }

    /** Counts slices taken from a direct-I/O input; vectors are read through slices. */
    final class Counting extends FilterIndexInput {
      Counting(IndexInput in) {
        super("Counting(" + in + ")", in);
      }

      @Override
      public IndexInput clone() {
        return new Counting(in.clone());
      }

      @Override
      public boolean isEfficientFor(IOContext context) {
        return in.isEfficientFor(context); // keep the wrapped input's answer
      }

      @Override
      public IndexInput slice(String desc, long offset, long length) throws IOException {
        directSlices.incrementAndGet();
        return in.slice(desc, offset, length);
      }
    }
  }

  private static MMapDirectory advisingByContext(Path path) throws IOException {
    MMapDirectory mmap = new MMapDirectory(path);
    mmap.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
    return mmap;
  }

  private static IndexWriter writer(RecordingDirectIODirectory dir) throws IOException {
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(new Lucene99HnswVectorsFormat()));
    iwc.setUseCompoundFile(false); // compound files are opened again without advice, by design
    LogDocMergePolicy mp = new LogDocMergePolicy();
    mp.setMergeFactor(1000); // only the explicit forceMerge merges
    iwc.setMergePolicy(mp);
    iwc.setMergeScheduler(new SerialMergeScheduler());
    return new IndexWriter(dir, iwc);
  }

  private static void addSegments(IndexWriter w, int segments) throws IOException {
    int id = 0;
    for (int s = 0; s < segments; s++) {
      for (int i = 0; i < DOCS_PER_SEGMENT; i++, id++) {
        Document doc = new Document();
        float[] v = new float[DIM];
        for (int d = 0; d < DIM; d++) v[d] = ((id * 31 + d * 7) % 101) / 101f + 0.01f;
        doc.add(new KnnFloatVectorField("v", v, VectorSimilarityFunction.EUCLIDEAN));
        doc.add(new StoredField("body", body(id)));
        w.addDocument(doc);
      }
      w.commit();
    }
  }

  /** About 100 hard-to-compress characters, so stored fields files span several pages. */
  private static String body(int id) {
    StringBuilder sb = new StringBuilder();
    long x = id * 0x9E3779B97F4A7C15L + 1;
    for (int i = 0; i < 100; i++) {
      x ^= x << 13;
      x ^= x >>> 7;
      x ^= x << 17;
      sb.append((char) ('a' + Math.floorMod(x, 26)));
    }
    return sb.toString();
  }

  /** The data files of the segments of {@code reader} with the given extension. */
  private static List<String> files(DirectoryReader reader, String extension) throws IOException {
    List<String> files = new ArrayList<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      for (String f : ((SegmentReader) ctx.reader()).getSegmentInfo().files()) {
        if (f.endsWith(extension)) files.add(f);
      }
    }
    return files;
  }

  private static void readSequentially(LeafReader leaf) throws IOException {
    FloatVectorValues values = leaf.getFloatVectorValues("v", DataAccessHint.SEQUENTIAL);
    for (int ord = 0; ord < values.size(); ord++) {
      values.vectorValue(ord);
    }
  }

  /**
   * Default delegate (what FSDirectory.open returns): merging segments that an NRT reader has open
   * reads their vectors and stored fields without direct I/O.
   */
  public void testMergeOfOpenSegmentsUsesDirectIOWithTheDefaultDelegate() throws Exception {
    try (RecordingDirectIODirectory dir =
            new RecordingDirectIODirectory(FSDirectory.open(createTempDir()));
        IndexWriter w = writer(dir)) {
      addSegments(w, 2);
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        assertEquals(2, nrt.leaves().size());
        dir.directOpens.clear();
        w.forceMerge(1);
        for (String f : files(nrt, ".vec")) {
          assertTrue("merge read " + f + " without direct I/O: " + dir.directOpens, dir.directOpens.contains(f));
        }
        for (String f : files(nrt, ".fdt")) {
          assertTrue("merge read " + f + " without direct I/O: " + dir.directOpens, dir.directOpens.contains(f));
        }
      }
    }
  }

  /** Control: with a delegate that advises by context the same merge uses direct I/O. */
  public void testControlMergeOfOpenSegmentsUsesDirectIOWithAnAdvisingDelegate() throws Exception {
    try (RecordingDirectIODirectory dir =
            new RecordingDirectIODirectory(advisingByContext(createTempDir()));
        IndexWriter w = writer(dir)) {
      addSegments(w, 2);
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        dir.directOpens.clear();
        w.forceMerge(1);
        for (String f : files(nrt, ".vec")) assertTrue(f + " " + dir.directOpens, dir.directOpens.contains(f));
        for (String f : files(nrt, ".fdt")) assertTrue(f + " " + dir.directOpens, dir.directOpens.contains(f));
      }
    }
  }

  /** A merge after a sequential read by a search reads through the search's input. */
  public void testMergeAfterSequentialSearchReadUsesDirectIO() throws Exception {
    try (RecordingDirectIODirectory dir =
            new RecordingDirectIODirectory(advisingByContext(createTempDir()));
        IndexWriter w = writer(dir)) {
      addSegments(w, 2);
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        for (LeafReaderContext ctx : nrt.leaves()) readSequentially(ctx.reader());
        dir.directOpens.clear();
        w.forceMerge(1);
        for (String f : files(nrt, ".vec")) {
          assertTrue(
              "merge after a sequential search read read " + f + " without direct I/O: " + dir.directOpens,
              dir.directOpens.contains(f));
        }
      }
    }
  }

  /** A sequential read by a search after a merge reads through the merge's direct-I/O input. */
  public void testSequentialSearchReadAfterMergeDoesNotUseDirectIO() throws Exception {
    try (RecordingDirectIODirectory dir =
            new RecordingDirectIODirectory(advisingByContext(createTempDir()));
        IndexWriter w = writer(dir)) {
      addSegments(w, 2);
      try (DirectoryReader nrt = DirectoryReader.open(w)) {
        w.forceMerge(1);
        assertFalse("the merge used direct I/O", dir.directOpens.isEmpty());
        // the NRT reader still has the merged-away segments open; a search reads them in order
        for (LeafReaderContext ctx : nrt.leaves()) {
          int before = dir.directSlices.get();
          readSequentially(ctx.reader());
          assertEquals(
              "a search read " + ctx.reader() + " through an input DirectIODirectory opened for merging",
              before,
              dir.directSlices.get());
        }
      }
    }
  }
}
