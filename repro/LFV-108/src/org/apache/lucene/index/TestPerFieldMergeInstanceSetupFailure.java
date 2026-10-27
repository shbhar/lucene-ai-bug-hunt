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
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.lucene99.Lucene99HnswVectorsFormat;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.store.DataAccessHint;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * PerFieldKnnVectorsFormat's merge instance calls getMergeInstance on the reader of each vector
 * field in turn. When a later field's getMergeInstance throws, the merge instances already obtained
 * for earlier fields are never finished: the per-field merge instance is never returned, so neither
 * MergeState nor SegmentMerger#cleanupMerge can reach them.
 *
 * <p>Two vector fields use two instances of a vectors format (so each has its own data file).
 * The format is registered by name (META-INF/services) so that segments read it back; it wraps
 * Lucene99HnswVectorsFormat and counts merge instances and finishMerge calls.
 */
public class TestPerFieldMergeInstanceSetupFailure extends LuceneTestCase {

  static final AtomicInteger ACQUIRED = new AtomicInteger();
  static final AtomicInteger FINISHED = new AtomicInteger();

  /** Lucene99HnswVectorsFormat whose readers count merge instances and finishMerge calls. */
  public static final class CountingHnswFormat extends KnnVectorsFormat {
    private final KnnVectorsFormat in = new Lucene99HnswVectorsFormat();

    public CountingHnswFormat() {
      super("TestCountingHnsw");
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      return in.fieldsWriter(state);
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new TestMergeInstanceSetupFailure.CountingKnnVectorsReader(
          in.fieldsReader(state), false, ACQUIRED, FINISHED);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return in.getMaxDimensions(fieldName);
    }
  }

  /** Fails the {@code failOn}-th open of a ".vec" file for sequential reads. */
  static final class FailingVecOpenDirectory extends FilterDirectory {
    final AtomicInteger opens = new AtomicInteger();
    volatile int failOn = Integer.MAX_VALUE;

    FailingVecOpenDirectory(Directory in) {
      super(in);
    }

    @Override
    public IndexInput openInput(String name, IOContext context) throws IOException {
      if (name.endsWith(".vec") && context.hints().contains(DataAccessHint.SEQUENTIAL)) {
        if (opens.incrementAndGet() == failOn) {
          throw new IOException("simulated failure to open " + name + " for sequential reads");
        }
      }
      return super.openInput(name, context);
    }
  }

  public void testSecondFieldFails() throws Exception {
    assertEquals(
        "the counting format must be registered (META-INF/services)",
        CountingHnswFormat.class,
        KnnVectorsFormat.forName("TestCountingHnsw").getClass());
    ACQUIRED.set(0);
    FINISHED.set(0);
    // two instances: each field gets its own suffix, reader and data file
    KnnVectorsFormat f1 = new CountingHnswFormat();
    KnnVectorsFormat f2 = new CountingHnswFormat();
    Codec delegate = TestUtil.getDefaultCodec(false);
    KnnVectorsFormat perField =
        new PerFieldKnnVectorsFormat() {
          @Override
          public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
            return field.equals("v1") ? f1 : f2;
          }
        };
    Codec codec =
        new FilterCodec(delegate.getName(), delegate) {
          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return perField;
          }
        };
    MMapDirectory mmap = new MMapDirectory(createTempDir("src"));
    mmap.setReadAdvice(MMapDirectory.ADVISE_BY_CONTEXT);
    FailingVecOpenDirectory srcDir = new FailingVecOpenDirectory(mmap);
    IndexWriterConfig config =
        new IndexWriterConfig()
            .setCodec(codec)
            .setUseCompoundFile(false)
            .setMergePolicy(NoMergePolicy.INSTANCE)
            .setMergeScheduler(new SerialMergeScheduler());
    try (IndexWriter src = new IndexWriter(srcDir, config);
        Directory destDir = newDirectory()) {
      for (int i = 0; i < 400; i++) {
        Document doc = new Document();
        doc.add(new StoredField("id", i));
        doc.add(new KnnFloatVectorField("v1", randomVector(16), VectorSimilarityFunction.EUCLIDEAN));
        doc.add(new KnnFloatVectorField("v2", randomVector(16), VectorSimilarityFunction.EUCLIDEAN));
        src.addDocument(doc);
      }
      src.flush();
      try (DirectoryReader reader = DirectoryReader.open(src)) {
        assertEquals(1, reader.leaves().size());
        CodecReader leaf = (CodecReader) reader.leaves().get(0).reader();
        // the open of field v2's data file fails, the one of v1 succeeded
        srcDir.failOn = srcDir.opens.get() + 2;
        try (IndexWriter dest =
            new IndexWriter(
                destDir, new IndexWriterConfig().setMergeScheduler(new SerialMergeScheduler()))) {
          IOException e = expectThrows(IOException.class, () -> dest.addIndexes(leaf));
          assertTrue(e.getMessage(), e.getMessage().contains("simulated failure"));
        }
        assertEquals(srcDir.failOn, srcDir.opens.get());
        assertEquals("field v1's merge instance was obtained", 1, ACQUIRED.get());
        assertEquals(
            "every vectors merge instance must be finished, also when the merge fails",
            ACQUIRED.get(),
            FINISHED.get());
      }
    }
  }

  private static float[] randomVector(int dim) {
    float[] v = new float[dim];
    for (int d = 0; d < dim; d++) {
      v[d] = random().nextFloat();
    }
    return v;
  }
}
