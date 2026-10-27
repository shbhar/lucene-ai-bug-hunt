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
package org.apache.lucene.codecs.lucene99;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.codecs.KnnVectorsReader;
import org.apache.lucene.codecs.KnnVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatFieldVectorsWriter;
import org.apache.lucene.codecs.hnsw.FlatVectorScorerUtil;
import org.apache.lucene.codecs.hnsw.FlatVectorsFormat;
import org.apache.lucene.codecs.hnsw.FlatVectorsScorer;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorScorer;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104ScalarQuantizedVectorsWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.column.Column;
import org.apache.lucene.document.column.ColumnBatch;
import org.apache.lucene.document.column.ObjectTupleCursor;
import org.apache.lucene.document.column.VectorColumn;
import org.apache.lucene.document.column.VectorValuesCursor;
import org.apache.lucene.index.CheckIndex;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocsWithFieldSet;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.tests.store.BaseDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.quantization.QuantizedByteVectorValues.ScalarEncoding;

/**
 * IndexWriter#addBatch hands a DENSE vector column to KnnFieldVectorsWriter#addDenseValues (#16734).
 * The default addDenseValues adds one vector at a time, so when the cursor rejects vector k it has
 * already recorded the first k vectors in the field writer, which its javadoc allows ("every doc ID
 * it has recorded must have its vector"). The HNSW and scalar-quantized field writers that wrap a
 * flat field writer update their own state (graph node counter, centroid sums and cached
 * magnitudes) only when the delegate returns normally, so after such a rejected batch they no
 * longer match the delegate. The delegate here is a per-field storage strategy plugged in through
 * the public {@code Lucene99FlatVectorsWriter(state, scorer, strategyFactory)} constructor that
 * does not override addDenseValues.
 *
 * <p>Public API only.
 */
public class TestDenseBatchWrapperDelegateDesync extends LuceneTestCase {

  private static final FlatVectorsScorer SCORER =
      FlatVectorScorerUtil.getLucene99FlatVectorsScorer();
  private static final FlatVectorsFormat FLAT_FORMAT = new Lucene99FlatVectorsFormat(SCORER);

  /** The stock HNSW writer over a flat writer with a custom per-field storage strategy. */
  private static final class StrategyHnswFormat extends KnnVectorsFormat {
    StrategyHnswFormat() {
      super("Lucene99HnswVectorsFormat"); // same on-disk format; read back by the stock reader
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      return new Lucene99HnswVectorsWriter(
          state,
          16,
          100,
          FLAT_FORMAT,
          new Lucene99FlatVectorsWriter(state, SCORER, ListFieldVectorsWriter::new),
          1,
          null,
          0); // tinySegmentsThreshold 0: build the graph from the first vector
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new Lucene99HnswVectorsFormat().fieldsReader(state);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return 1024;
    }
  }

  /** The stock flat scalar-quantized writer over the same custom raw-vector storage. */
  private static final class StrategyScalarQuantizedFormat extends KnnVectorsFormat {
    StrategyScalarQuantizedFormat() {
      super("Lucene104ScalarQuantizedVectorsFormat");
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) throws IOException {
      return new Lucene104ScalarQuantizedVectorsWriter(
          state,
          ScalarEncoding.UNSIGNED_BYTE,
          new Lucene99FlatVectorsWriter(state, SCORER, ListFieldVectorsWriter::new),
          new Lucene104ScalarQuantizedVectorScorer(SCORER));
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) throws IOException {
      return new Lucene104ScalarQuantizedVectorsFormat().fieldsReader(state);
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return 1024;
    }
  }

  /**
   * Per-field float vector storage in an on-heap list. Like every field writer written before
   * #16734 (for example the paged writer in TestKnnVectorsFormatCustomWriter), it does not override
   * addDenseValues and so uses the documented default.
   */
  private static final class ListFieldVectorsWriter extends FlatFieldVectorsWriter<float[]> {
    private final FieldInfo fieldInfo;
    private final List<float[]> vectors = new ArrayList<>();
    private final DocsWithFieldSet docsWithField = new DocsWithFieldSet();
    private int lastDocID = -1;
    private boolean finished;

    ListFieldVectorsWriter(FieldInfo fieldInfo) {
      this.fieldInfo = fieldInfo;
    }

    @Override
    public void addValue(int docID, float[] vectorValue) {
      if (finished) {
        throw new IllegalStateException("already finished");
      }
      if (docID == lastDocID) {
        throw new IllegalArgumentException("only one value per document");
      }
      vectors.add(copyValue(vectorValue));
      docsWithField.add(docID);
      lastDocID = docID;
    }

    @Override
    public float[] copyValue(float[] vectorValue) {
      return ArrayUtil.copyOfSubArray(vectorValue, 0, fieldInfo.getVectorDimension());
    }

    @Override
    public List<float[]> getVectors() {
      return vectors;
    }

    @Override
    public DocsWithFieldSet getDocsWithFieldSet() {
      return docsWithField;
    }

    @Override
    public void finish() {
      finished = true;
    }

    @Override
    public boolean isFinished() {
      return finished;
    }

    @Override
    public long ramBytesUsed() {
      return 64L + vectors.size() * (16L + 4L * fieldInfo.getVectorDimension());
    }
  }

  /** A float vector column over in-memory rows. */
  private static final class RowsColumn extends VectorColumn<float[]> {
    private final float[][] rows;

    RowsColumn(String name, FieldType fieldType, Density density, float[][] rows) {
      super(name, fieldType, density);
      this.rows = rows;
    }

    @Override
    public ObjectTupleCursor<float[]> tuples() {
      return new ObjectTupleCursor<>() {
        int pos = -1;

        @Override
        public int nextDoc() {
          pos++;
          return pos < rows.length ? pos : DocIdSetIterator.NO_MORE_DOCS;
        }

        @Override
        public float[] value() {
          return rows[pos];
        }
      };
    }

    @Override
    public VectorValuesCursor<float[]> values() {
      return new VectorValuesCursor<>(rows.length, fieldType().vectorDimension()) {
        int pos;

        @Override
        public float[] next() {
          if (pos >= size()) {
            throw new IllegalStateException("consumed past size()");
          }
          return rows[pos++];
        }
      };
    }
  }

  private static ColumnBatch batch(Column column, int numDocs) {
    return new ColumnBatch() {
      @Override
      public int numDocs() {
        return numDocs;
      }

      @Override
      public Iterable<Column> columns() {
        return List.of(column);
      }
    };
  }

  private static Document vectorDoc(float[] vector, FieldType type) {
    Document doc = new Document();
    doc.add(new KnnFloatVectorField("v", vector, type));
    return doc;
  }

  /**
   * Indexes doc 0 = {@code first}, then a 3-document batch whose third vector addBatch rejects
   * (docs 1..3, deleted), then doc 4 = {@code last}; commits and checks the result. Returns the
   * list of contract violations (empty when the index is correct).
   */
  private static List<String> indexAndCheck(
      KnnVectorsFormat format,
      VectorSimilarityFunction similarity,
      Column.Density density,
      float[] first,
      float[][] rejectedBatch,
      float[] last)
      throws IOException {
    List<String> problems = new ArrayList<>();
    FieldType type = KnnFloatVectorField.createFieldType(first.length, similarity);
    try (BaseDirectoryWrapper dir = newDirectory()) {
      dir.setCheckIndexOnClose(false); // checked explicitly below
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysKnnVectorsFormat(format))
              .setMergePolicy(NoMergePolicy.INSTANCE)
              .setMergeScheduler(new SerialMergeScheduler());
      IndexWriter w = new IndexWriter(dir, iwc);
      try {
        w.addDocument(vectorDoc(first, type));
        IllegalArgumentException rejected =
            expectThrows(
                IllegalArgumentException.class,
                () -> w.addBatch(batch(new RowsColumn("v", type, density, rejectedBatch), 3)));
        assertNull("the rejected batch must not close the writer", w.getTragicException());
        assertTrue(rejected.getMessage(), rejected.getMessage().contains("at batch doc 2"));
        w.addDocument(vectorDoc(last, type));
        try {
          w.commit();
        } catch (Throwable t) {
          StackTraceElement top = t.getStackTrace().length > 0 ? t.getStackTrace()[0] : null;
          problems.add(
              "commit() threw "
                  + t
                  + " at "
                  + top
                  + "; tragic exception="
                  + w.getTragicException()
                  + "; writer open="
                  + w.isOpen());
          return problems;
        }
      } finally {
        if (w.isOpen()) {
          w.close();
        }
      }

      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        if (reader.numDocs() != 2 || reader.maxDoc() != 5) {
          problems.add("numDocs=" + reader.numDocs() + " maxDoc=" + reader.maxDoc());
        }
        LeafReader leaf = getOnlyLeafReader(reader);
        // each live document is the exact nearest neighbour of its own vector
        IndexSearcher searcher = new IndexSearcher(reader);
        int[] expectedDoc = {0, 4};
        float[][] queries = {first, last};
        for (int i = 0; i < 2; i++) {
          ScoreDoc[] hits;
          try {
            hits = searcher.search(new KnnFloatVectorQuery("v", queries[i], 1), 1).scoreDocs;
          } catch (Throwable t) {
            problems.add("kNN query threw " + t);
            break;
          }
          if (hits.length != 1 || hits[0].doc != expectedDoc[i]) {
            problems.add(
                "kNN k=1 for doc "
                    + expectedDoc[i]
                    + "'s own vector returned "
                    + (hits.length == 0 ? "no hit" : "doc " + hits[0].doc));
          }
        }
        FloatVectorValues values = leaf.getFloatVectorValues("v");
        if (values.size() > 5) {
          problems.add("segment holds " + values.size() + " vectors for 5 documents");
        }
      }
      try (CheckIndex checker = new CheckIndex(dir)) {
        checker.setThreadCount(1);
        checker.setLevel(CheckIndex.Level.MIN_LEVEL_FOR_SLOW_CHECKS);
        // fail fast: without it CheckIndex prints an HNSW graph error but still reports the index
        // as clean (a separate CheckIndex issue), as TestUtil.checkIndex does
        checker.setFailFast(true);
        CheckIndex.Status status = checker.checkIndex();
        if (status.clean == false) {
          problems.add("CheckIndex reports the index as broken");
        }
      } catch (Throwable t) {
        problems.add("CheckIndex threw " + t);
      }
    }
    return problems;
  }

  /** Float vectors; the third vector of the batch is rejected (NaN). */
  private static final float[] FIRST = {1f, 0f};

  private static final float[][] BATCH_WITH_NAN = {{0f, 1f}, {1f, 1f}, {Float.NaN, 0f}};
  private static final float[] LAST = {-1f, 0f};

  public void testHnswWriterOverDefaultAddDenseValuesDelegate() throws IOException {
    List<String> problems =
        indexAndCheck(
            new StrategyHnswFormat(),
            VectorSimilarityFunction.EUCLIDEAN,
            Column.Density.DENSE,
            FIRST,
            BATCH_WITH_NAN,
            LAST);
    assertTrue("rejected DENSE batch left the HNSW writer inconsistent: " + problems, problems.isEmpty());
  }

  public void testScalarQuantizedWriterOverDefaultAddDenseValuesDelegate() throws IOException {
    // COSINE: the third vector of the batch is a zero vector, which addBatch rejects
    float[][] batchWithZero = {{0f, 1f}, {1f, 1f}, {0f, 0f}};
    List<String> problems =
        indexAndCheck(
            new StrategyScalarQuantizedFormat(),
            VectorSimilarityFunction.COSINE,
            Column.Density.DENSE,
            FIRST,
            batchWithZero,
            new float[] {-1f, 0.5f});
    assertTrue(
        "rejected DENSE batch left the scalar-quantized writer inconsistent: " + problems,
        problems.isEmpty());
  }

  /** Control: the same rows as a SPARSE column (tuple path, one addValue per vector) are fine. */
  public void testControlSparseColumnSameRows() throws IOException {
    assertEquals(
        List.of(),
        indexAndCheck(
            new StrategyHnswFormat(),
            VectorSimilarityFunction.EUCLIDEAN,
            Column.Density.SPARSE,
            FIRST,
            BATCH_WITH_NAN,
            LAST));
    assertEquals(
        List.of(),
        indexAndCheck(
            new StrategyScalarQuantizedFormat(),
            VectorSimilarityFunction.COSINE,
            Column.Density.SPARSE,
            FIRST,
            new float[][] {{0f, 1f}, {1f, 1f}, {0f, 0f}},
            new float[] {-1f, 0.5f}));
  }

  /** Control: the stock formats (whose flat field writer overrides addDenseValues) are fine. */
  public void testControlStockFormats() throws IOException {
    assertEquals(
        List.of(),
        indexAndCheck(
            new Lucene99HnswVectorsFormat(16, 100, 0),
            VectorSimilarityFunction.EUCLIDEAN,
            Column.Density.DENSE,
            FIRST,
            BATCH_WITH_NAN,
            LAST));
    assertEquals(
        List.of(),
        indexAndCheck(
            new Lucene104ScalarQuantizedVectorsFormat(),
            VectorSimilarityFunction.COSINE,
            Column.Density.DENSE,
            FIRST,
            new float[][] {{0f, 1f}, {1f, 1f}, {0f, 0f}},
            new float[] {-1f, 0.5f}));
  }
}
