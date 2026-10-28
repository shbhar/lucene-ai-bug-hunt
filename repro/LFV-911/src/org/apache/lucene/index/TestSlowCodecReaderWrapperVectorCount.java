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
import org.apache.lucene.codecs.KnnFieldVectorsWriter;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.IORunnable;

/**
 * KnnVectorsReader#getVectorCount: "This has the same meaning as get*VectorValues(field).size()".
 *
 * <p>SlowCodecReaderWrapper.wrap(view) returns "a CodecReader view of reader"; its KnnVectorsReader
 * forwards get*VectorValues to the view, but answers getVectorCount from the SegmentReader found by
 * stripping every FilterLeafReader / FilterCodecReader layer. A FilterLeafReader that transforms the
 * vectors of a field ("possibly transforming the data along the way", FilterLeafReader javadoc) gets
 * a count that disagrees with the values the same KnnVectorsReader returns.
 *
 * <p>Uses public API only. The first two tests read the wrapper's KnnVectorsReader through
 * CodecReader#getVectorReader (an expert accessor); the merge tests observe the same reader the way
 * a codec does during IndexWriter.addIndexes, through MergeState.knnVectorsReaders.
 */
public class TestSlowCodecReaderWrapperVectorCount extends LuceneTestCase {

  private static final int NUM_DOCS = 6;

  /** One segment, NUM_DOCS docs, every doc has float vector "vf" and byte vector "vb". */
  private static Directory buildIndex() throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < NUM_DOCS; i++) {
        Document d = new Document();
        d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
        d.add(new KnnFloatVectorField("vf", new float[] {i + 1, 1f}, VectorSimilarityFunction.EUCLIDEAN));
        d.add(new KnnByteVectorField("vb", new byte[] {(byte) (i + 1), 1}, VectorSimilarityFunction.EUCLIDEAN));
        w.addDocument(d);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** A view that exposes the vectors of even documents only (e.g. a document-level projection). */
  private static final class EvenDocsVectorsReader extends FilterLeafReader {
    EvenDocsVectorsReader(LeafReader in) {
      super(in);
    }

    @Override
    public FloatVectorValues getFloatVectorValues(String field) throws IOException {
      FloatVectorValues in = super.getFloatVectorValues(field);
      if (in == null) {
        return null;
      }
      List<float[]> vectors = new ArrayList<>();
      List<Integer> docs = new ArrayList<>();
      KnnVectorValues.DocIndexIterator it = in.iterator();
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        if (doc % 2 == 0) {
          vectors.add(in.vectorValue(it.index()).clone());
          docs.add(doc);
        }
      }
      int dim = in.dimension();
      return new FloatVectorValues() {
        @Override
        public float[] vectorValue(int ord) {
          return vectors.get(ord);
        }

        @Override
        public int dimension() {
          return dim;
        }

        @Override
        public int size() {
          return vectors.size();
        }

        @Override
        public int ordToDoc(int ord) {
          return docs.get(ord);
        }

        @Override
        public FloatVectorValues copy() {
          return this;
        }

        @Override
        public DocIndexIterator iterator() {
          return createSparseIterator();
        }
      };
    }

    @Override
    public ByteVectorValues getByteVectorValues(String field) throws IOException {
      ByteVectorValues in = super.getByteVectorValues(field);
      if (in == null) {
        return null;
      }
      List<byte[]> vectors = new ArrayList<>();
      List<Integer> docs = new ArrayList<>();
      KnnVectorValues.DocIndexIterator it = in.iterator();
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        if (doc % 2 == 0) {
          vectors.add(in.vectorValue(it.index()).clone());
          docs.add(doc);
        }
      }
      int dim = in.dimension();
      return new ByteVectorValues() {
        @Override
        public byte[] vectorValue(int ord) {
          return vectors.get(ord);
        }

        @Override
        public int dimension() {
          return dim;
        }

        @Override
        public int size() {
          return vectors.size();
        }

        @Override
        public int ordToDoc(int ord) {
          return docs.get(ord);
        }

        @Override
        public ByteVectorValues copy() {
          return this;
        }

        @Override
        public DocIndexIterator iterator() {
          return createSparseIterator();
        }
      };
    }

    @Override
    public CacheHelper getCoreCacheHelper() {
      return null;
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }

  public void testFloatVectorCountMatchesValuesOfWrappedView() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader view = new EvenDocsVectorsReader(getOnlyLeafReader(r));
      CodecReader codecView = SlowCodecReaderWrapper.wrap(view);
      KnnVectorsReader vectors = codecView.getVectorReader();
      FieldInfo fi = codecView.getFieldInfos().fieldInfo("vf");
      int size = vectors.getFloatVectorValues("vf").size();
      assertEquals("control: the view exposes 3 of 6 vectors", 3, size);
      // documented: getVectorCount has the same meaning as getFloatVectorValues(field).size()
      assertEquals(
          "getVectorCount must equal getFloatVectorValues(vf).size() of the same reader",
          size,
          vectors.getVectorCount(fi));
    }
  }

  public void testByteVectorCountMatchesValuesOfWrappedView() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader view = new EvenDocsVectorsReader(getOnlyLeafReader(r));
      CodecReader codecView = SlowCodecReaderWrapper.wrap(view);
      KnnVectorsReader vectors = codecView.getVectorReader();
      FieldInfo fi = codecView.getFieldInfos().fieldInfo("vb");
      int size = vectors.getByteVectorValues("vb").size();
      assertEquals("control: the view exposes 3 of 6 vectors", 3, size);
      assertEquals(
          "getVectorCount must equal getByteVectorValues(vb).size() of the same reader",
          size,
          vectors.getVectorCount(fi));
    }
  }

  /**
   * A vectors format that, while merging, records what each incoming KnnVectorsReader reports for
   * a field: getVectorCount and getFloatVectorValues(field).size(). It writes with, and is named
   * like, the default format, so the merged segment stays readable.
   */
  private static final class CountRecordingFormat extends KnnVectorsFormat {
    final KnnVectorsFormat in = TestUtil.getDefaultKnnVectorsFormat();
    final List<String> seen = new ArrayList<>();

    CountRecordingFormat() {
      super(TestUtil.getDefaultKnnVectorsFormat().getName());
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      KnnVectorsWriter w = in.fieldsWriter(state);
      return new KnnVectorsWriter() {
        @Override
        public KnnFieldVectorsWriter<?> addField(FieldInfo fieldInfo) throws IOException {
          return w.addField(fieldInfo);
        }

        @Override
        public void flush(int maxDoc, Sorter.DocMap sortMap) throws IOException {
          w.flush(maxDoc, sortMap);
        }

        @Override
        public IORunnable mergeOneField(FieldInfo fieldInfo, MergeState mergeState)
            throws IOException {
          for (int i = 0; i < mergeState.knnVectorsReaders.length; i++) {
            KnnVectorsReader r = mergeState.knnVectorsReaders[i];
            FieldInfo fi = mergeState.fieldInfos[i].fieldInfo(fieldInfo.name);
            if (r != null && fi != null && fi.hasVectorValues()) {
              int size =
                  fi.getVectorEncoding() == VectorEncoding.BYTE
                      ? r.getByteVectorValues(fi.name).size()
                      : r.getFloatVectorValues(fi.name).size();
              String count;
              try {
                count = Integer.toString(r.getVectorCount(fi));
              } catch (RuntimeException e) {
                count = e.getClass().getSimpleName();
              }
              seen.add(fi.name + ": count=" + count + " size=" + size);
            }
          }
          return w.mergeOneField(fieldInfo, mergeState);
        }

        @Override
        public void finish() throws IOException {
          w.finish();
        }

        @Override
        public long ramBytesUsed() {
          return w.ramBytesUsed();
        }

        @Override
        public void close() throws IOException {
          w.close();
        }
      };
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return in.fieldsReader(state);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return in.getMaxDimensions(fieldName);
    }
  }

  private static IndexWriterConfig mergeProbeConfig(CountRecordingFormat format) {
    // serial merges: the probe runs in the test thread
    return new IndexWriterConfig()
        .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
        .setMergeScheduler(new SerialMergeScheduler());
  }

  /**
   * IndexWriter.addIndexes(SlowCodecReaderWrapper.wrap(view)) hands the wrapper's KnnVectorsReader
   * to the codec's vectors writer (MergeState.knnVectorsReaders); a writer that asks it for the
   * count gets the segment's count, not the 3 vectors it is about to merge.
   */
  public void testCountSeenByCodecDuringAddIndexesMatchesValues() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir);
        Directory dest = newDirectory()) {
      CodecReader codecView =
          SlowCodecReaderWrapper.wrap(new EvenDocsVectorsReader(getOnlyLeafReader(r)));
      CountRecordingFormat format = new CountRecordingFormat();
      try (IndexWriter w = new IndexWriter(dest, mergeProbeConfig(format))) {
        w.addIndexes(codecView);
      }
      assertEquals(
          "what the codec sees while merging the view",
          List.of("vf: count=3 size=3", "vb: count=3 size=3"),
          format.seen);
    }
  }

  /**
   * A view that adds a vector field to a segment that has no vector fields. addIndexes copies it,
   * but a codec that asks the wrapper's KnnVectorsReader for the count during that merge gets a
   * NullPointerException, because the count is looked up in the vector-less segment.
   */
  public void testCountOfVectorFieldAddedByViewDuringAddIndexes() throws IOException {
    try (Directory plain = newDirectory();
        Directory withVectors = buildIndex();
        Directory dest = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(plain, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document d = new Document();
          d.add(new StringField("id", Integer.toString(i), Field.Store.NO));
          w.addDocument(d);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader rp = DirectoryReader.open(plain);
          DirectoryReader rv = DirectoryReader.open(withVectors)) {
        LeafReader source = getOnlyLeafReader(rv);
        // same field layout as the source index: id, then the vector fields
        LeafReader view =
            new FilterLeafReader(getOnlyLeafReader(rp)) {
              @Override
              public FieldInfos getFieldInfos() {
                return source.getFieldInfos();
              }

              @Override
              public FloatVectorValues getFloatVectorValues(String field) throws IOException {
                return source.getFloatVectorValues(field);
              }

              @Override
              public ByteVectorValues getByteVectorValues(String field) throws IOException {
                return source.getByteVectorValues(field);
              }

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        CountRecordingFormat format = new CountRecordingFormat();
        try (IndexWriter w = new IndexWriter(dest, mergeProbeConfig(format))) {
          w.addIndexes(SlowCodecReaderWrapper.wrap(view));
        }
        assertEquals(
            "what the codec sees while merging the view",
            List.of("vf: count=6 size=6", "vb: count=6 size=6"),
            format.seen);
      }
    }
  }

  /** Control: the writer, reading the same CodecReader, copies the 3 vectors the view exposes. */
  public void testAddIndexesCopiesTheViewsVectors() throws IOException {
    try (Directory dir = buildIndex();
        DirectoryReader r = DirectoryReader.open(dir);
        Directory dest = newDirectory()) {
      CodecReader codecView = SlowCodecReaderWrapper.wrap(new EvenDocsVectorsReader(getOnlyLeafReader(r)));
      try (IndexWriter w = new IndexWriter(dest, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        w.addIndexes(codecView);
        w.commit();
      }
      try (DirectoryReader copy = DirectoryReader.open(dest)) {
        LeafReader leaf = getOnlyLeafReader(copy);
        assertEquals(3, leaf.getFloatVectorValues("vf").size());
        assertEquals(3, leaf.getByteVectorValues("vb").size());
      }
    }
  }
}
