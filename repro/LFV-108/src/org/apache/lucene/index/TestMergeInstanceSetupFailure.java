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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.search.AcceptDocs;
import org.apache.lucene.search.KnnCollector;
import org.apache.lucene.store.DataAccessHint;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.codecs.asserting.AssertingCodec;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * KnnVectorsReader#finishMerge must run for every merge instance obtained with getMergeInstance,
 * including when the merge fails (#14930, fixed for the merge body by #14977). Merge instances are
 * obtained in the MergeState constructor, which runs before the try/finally that calls
 * SegmentMerger#cleanupMerge. When the constructor throws after it obtained a merge instance, that
 * merge instance is never finished.
 *
 * <p>Trigger: MMapDirectory with ADVISE_BY_CONTEXT, so the stored fields and vectors of a segment
 * are read through RANDOM-advised inputs and getMergeInstance opens the data file again for
 * sequential reads (#16883). That second open fails with an IOException for the second segment of
 * the merge (as when the process runs out of file descriptors). The vectors merge instance of the
 * first segment is then never finished.
 */
public class TestMergeInstanceSetupFailure extends LuceneTestCase {

  /** Counts merge instances of the vectors reader of the codec and their finishMerge calls. */
  static final AtomicInteger KNN_ACQUIRED = new AtomicInteger();

  static final AtomicInteger KNN_FINISHED = new AtomicInteger();

  static final class CountingKnnVectorsFormat extends KnnVectorsFormat {
    private final KnnVectorsFormat in;

    CountingKnnVectorsFormat(KnnVectorsFormat in) {
      super(in.getName());
      this.in = in;
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      return in.fieldsWriter(state);
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new CountingKnnVectorsReader(in.fieldsReader(state), false, KNN_ACQUIRED, KNN_FINISHED);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return in.getMaxDimensions(fieldName);
    }
  }

  static final class CountingKnnVectorsReader extends KnnVectorsReader {
    private final KnnVectorsReader in;
    private final boolean mergeInstance;
    private final AtomicInteger acquired;
    private final AtomicInteger finishedCount;
    private boolean finished;

    CountingKnnVectorsReader(
        KnnVectorsReader in,
        boolean mergeInstance,
        AtomicInteger acquired,
        AtomicInteger finishedCount) {
      this.in = in;
      this.mergeInstance = mergeInstance;
      this.acquired = acquired;
      this.finishedCount = finishedCount;
    }

    @Override
    public KnnVectorsReader getMergeInstance() throws IOException {
      KnnVectorsReader merge = in.getMergeInstance();
      acquired.incrementAndGet();
      return new CountingKnnVectorsReader(merge, true, acquired, finishedCount);
    }

    @Override
    public void finishMerge() throws IOException {
      assertTrue("finishMerge on a merge instance", mergeInstance);
      assertFalse("finishMerge called twice", finished);
      finished = true;
      finishedCount.incrementAndGet();
      in.finishMerge();
    }

    @Override
    public KnnVectorsReader unwrapReaderForField(String field) {
      return in.unwrapReaderForField(field);
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
    public FloatVectorValues getFloatVectorValues(String field, DataAccessHint access)
        throws IOException {
      return in.getFloatVectorValues(field, access);
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
    public void search(String field, float[] target, KnnCollector c, AcceptDocs acceptDocs)
        throws IOException {
      in.search(field, target, c, acceptDocs);
    }

    @Override
    public void search(String field, byte[] target, KnnCollector c, AcceptDocs acceptDocs)
        throws IOException {
      in.search(field, target, c, acceptDocs);
    }

    @Override
    public void search(String field, short[] target, KnnCollector c, AcceptDocs acceptDocs)
        throws IOException {
      in.search(field, target, c, acceptDocs);
    }

    @Override
    public int getVectorCount(FieldInfo fieldInfo) throws IOException {
      return in.getVectorCount(fieldInfo);
    }

    @Override
    public Map<String, Long> getOffHeapByteSize(FieldInfo fieldInfo) {
      return in.getOffHeapByteSize(fieldInfo);
    }

    @Override
    public void close() throws IOException {
      in.close();
    }
  }

  /** The default codec, with the vectors reader wrapped to count merge instances. */
  static Codec countingCodec(boolean useCompoundFile) {
    Codec delegate = TestUtil.getDefaultCodec(useCompoundFile);
    KnnVectorsFormat knn = new CountingKnnVectorsFormat(delegate.knnVectorsFormat());
    return new FilterCodec(delegate.getName(), delegate) {
      @Override
      public KnnVectorsFormat knnVectorsFormat() {
        return knn;
      }
    };
  }

  /**
   * Counts the opens of ".fdt" files for sequential reads (the open that the stored fields
   * getMergeInstance does when the held input is advised for random reads) and of ".cfs" files
   * (the compound reader opens another copy for such reads), and fails the {@code failOn}-th one.
   */
  static final class FailingSequentialOpenDirectory extends FilterDirectory {
    final AtomicInteger sequentialOpens = new AtomicInteger();
    volatile int failOn = Integer.MAX_VALUE;

    FailingSequentialOpenDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      if ((name.endsWith(".fdt") && context.hints().contains(DataAccessHint.SEQUENTIAL))
          || name.endsWith(".cfs")) {
        if (sequentialOpens.incrementAndGet() == failOn) {
          throw new IOException("simulated failure to open " + name + " for sequential reads");
        }
      }
      return super.openInput(name, context);
    }
  }

  private static void addSegment(IndexWriter w, int base, int numDocs) throws IOException {
    for (int i = 0; i < numDocs; i++) {
      Document doc = new Document();
      doc.add(new StoredField("id", base + i));
      doc.add(new StoredField("text", TestUtil.randomSimpleString(random(), 64, 96)));
      float[] v = new float[16];
      for (int d = 0; d < v.length; d++) {
        v[d] = random().nextFloat();
      }
      doc.add(new KnnFloatVectorField("vec", v, VectorSimilarityFunction.EUCLIDEAN));
      w.addDocument(doc);
    }
    w.flush();
  }

  private void doTestAddIndexes(boolean useCompoundFile) throws Exception {
    KNN_ACQUIRED.set(0);
    KNN_FINISHED.set(0);
    MMapDirectory mmap = new MMapDirectory(createTempDir("src"));
    mmap.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
    FailingSequentialOpenDirectory srcDir = new FailingSequentialOpenDirectory(mmap);
    IndexWriterConfig srcConfig =
        new IndexWriterConfig()
            .setCodec(countingCodec(useCompoundFile))
            .setUseCompoundFile(useCompoundFile)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler());
    try (IndexWriter src = new IndexWriter(srcDir, srcConfig);
        Directory destDir = newDirectory()) {
      addSegment(src, 0, 400);
      addSegment(src, 400, 400);
      try (DirectoryReader reader = DirectoryReader.open(src)) {
        assertEquals(2, reader.leaves().size());
        List<CodecReader> leaves = new ArrayList<>();
        for (LeafReaderContext ctx : reader.leaves()) {
          leaves.add((CodecReader) ctx.reader());
        }
        // the second segment's merge open fails: that of the first segment succeeded
        final int before = srcDir.sequentialOpens.get();
        srcDir.failOn = before + 2;
        try (IndexWriter dest =
            new IndexWriter(
                destDir, new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler()))) {
          IOException e =
              expectThrows(
                  IOException.class, () -> dest.addIndexes(leaves.toArray(new CodecReader[0])));
          assertTrue(e.getMessage(), e.getMessage().contains("simulated failure"));
        }
        // the failure is the second such open, so the first segment's open happened
        assertEquals(before + 2, srcDir.sequentialOpens.get());
        assertTrue("the first segment's vectors merge instance was obtained", KNN_ACQUIRED.get() > 0);
        assertEquals(
            "every vectors merge instance must be finished, also when the merge fails",
            KNN_ACQUIRED.get(),
            KNN_FINISHED.get());
      }
    }
  }

  public void testAddIndexesSetupFailure() throws Exception {
    doTestAddIndexes(false);
  }

  public void testAddIndexesSetupFailureCompoundFile() throws Exception {
    doTestAddIndexes(true);
  }

  /** Same through IndexWriter#forceMerge, where the merge failure is tragic. */
  public void testForceMergeSetupFailure() throws Exception {
    KNN_ACQUIRED.set(0);
    KNN_FINISHED.set(0);
    MMapDirectory mmap = new MMapDirectory(createTempDir("src"));
    mmap.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
    FailingSequentialOpenDirectory dir = new FailingSequentialOpenDirectory(mmap);
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setCodec(countingCodec(false))
            .setUseCompoundFile(false)
            .setMergePolicy(new TieredMergePolicy())
            .setMergeScheduler(new SerialMergeScheduler());
    IndexWriter w = new IndexWriter(dir, config);
    try {
      addSegment(w, 0, 400);
      addSegment(w, 400, 400);
      // keep an NRT reader open so the merge's readers are pooled and opened for searches first
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(2, reader.leaves().size());
        final int before = dir.sequentialOpens.get();
        dir.failOn = before + 2;
        IOException e = expectThrows(IOException.class, () -> w.forceMerge(1));
        assertNotNull(e);
        assertEquals(before + 2, dir.sequentialOpens.get());
        assertTrue(KNN_ACQUIRED.get() > 0);
        assertEquals(
            "every vectors merge instance must be finished, also when the merge fails",
            KNN_ACQUIRED.get(),
            KNN_FINISHED.get());
      }
    } finally {
      w.rollback();
    }
  }

  /**
   * The test framework's AssertingKnnVectorsReader checks on close that every merge instance it
   * handed out was finished (mergeInstanceCount == finishMergeCount). With assertions enabled,
   * closing the readers after a failed merge setup trips that check.
   */
  public void testAssertingCodecCloseAfterSetupFailure() throws Exception {
    MMapDirectory mmap = new MMapDirectory(createTempDir("src"));
    mmap.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
    FailingSequentialOpenDirectory srcDir = new FailingSequentialOpenDirectory(mmap);
    IndexWriterConfig srcConfig =
        new IndexWriterConfig()
            .setCodec(new AssertingCodec())
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler());
    IndexWriter src = new IndexWriter(srcDir, srcConfig);
    Directory destDir = newDirectory();
    DirectoryReader reader = null;
    try {
      addSegment(src, 0, 400);
      addSegment(src, 400, 400);
      reader = DirectoryReader.open(src);
      List<CodecReader> leaves = new ArrayList<>();
      for (LeafReaderContext ctx : reader.leaves()) {
        leaves.add((CodecReader) ctx.reader());
      }
      final int before = srcDir.sequentialOpens.get();
      srcDir.failOn = before + 2;
      try (IndexWriter dest =
          new IndexWriter(
              destDir, new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler()))) {
        expectThrows(IOException.class, () -> dest.addIndexes(leaves.toArray(new CodecReader[0])));
      }
      assertEquals(before + 2, srcDir.sequentialOpens.get());
    } finally {
      srcDir.failOn = Integer.MAX_VALUE;
      // closing the last references closes the segment readers
      DirectoryReader r = reader;
      Throwable t = null;
      try {
        if (r != null) {
          r.close();
        }
        src.close();
      } catch (Throwable e) {
        t = e;
      }
      destDir.close();
      if (t != null) {
        throw new AssertionError("closing the readers after the failed merge threw", t);
      }
    }
  }
}
