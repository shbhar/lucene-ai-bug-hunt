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
package org.apache.lucene.codecs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.codecs.perfield.PerFieldKnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * KnnVectorValues#ordToDoc(ord) must return "the docid of the document indexed with the given
 * vector ordinal". Checked on the values BufferingKnnVectorsWriter (a public, experimental
 * extension point) passes to writeField at flush, for a field that only some documents have, with
 * and without an index sort: for every document reached by nextDoc(), ordToDoc(index()) == docID().
 */
public class TestBufferedVectorValuesOrdToDoc extends LuceneTestCase {

  /** "docID->ordToDoc(index())" pairs observed inside writeField */
  static final List<String> SEEN = new ArrayList<>();

  static final class ProbeFormat extends KnnVectorsFormat {
    ProbeFormat() {
      super("ProbeBufferingFormat");
    }

    @Override
    public KnnVectorsWriter fieldsWriter(SegmentWriteState state) {
      return new BufferingKnnVectorsWriter() {
        @Override
        protected void writeField(FieldInfo fieldInfo, FloatVectorValues values, int maxDoc)
            throws IOException {
          KnnVectorValues.DocIndexIterator it = values.iterator();
          for (int doc = it.nextDoc(); doc != KnnVectorValues.DocIndexIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
            SEEN.add(doc + "->" + values.ordToDoc(it.index()));
          }
        }

        @Override
        protected void writeField(FieldInfo fieldInfo, Float16VectorValues values, int maxDoc) {}

        @Override
        protected void writeField(FieldInfo fieldInfo, ByteVectorValues values, int maxDoc) {}

        @Override
        public void finish() {}

        @Override
        public void close() {}
      };
    }

    @Override
    public KnnVectorsReader fieldsReader(SegmentReadState state) {
      throw new UnsupportedOperationException("this test never opens a reader");
    }

    @Override
    public int getMaxDimensions(String fieldName) {
      return 1024;
    }
  }

  private static List<String> flush(Sort indexSort) throws IOException {
    SEEN.clear();
    KnnVectorsFormat probe = new ProbeFormat();
    Codec base = TestUtil.getDefaultCodec();
    Codec codec =
        new FilterCodec(base.getName(), base) {
          final KnnVectorsFormat perField =
              new PerFieldKnnVectorsFormat() {
                @Override
                public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                  return probe;
                }
              };

          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return perField;
          }
        };
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(codec);
    if (indexSort != null) {
      iwc.setIndexSort(indexSort);
    }
    // a plain directory: the segment uses a write-only test format and is never opened
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      // documents 0, 2, 3 have a vector; document 1 has none
      for (int d = 0; d < 4; d++) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("rev", -d));
        if (d != 1) {
          doc.add(new KnnFloatVectorField("v", new float[] {d}, VectorSimilarityFunction.EUCLIDEAN));
        }
        w.addDocument(doc);
      }
      w.commit();
    }
    return new ArrayList<>(SEEN);
  }

  public void testSparseField() throws Exception {
    assertEquals(List.of("0->0", "2->2", "3->3"), flush(null));
  }

  public void testSparseFieldWithIndexSort() throws Exception {
    // the index sort reverses the documents: old 3, 2, 1, 0 become 0, 1, 2, 3; old 1 has no vector
    assertEquals(
        List.of("0->0", "1->1", "3->3"), flush(new Sort(new SortField("rev", SortField.Type.LONG))));
  }
}
