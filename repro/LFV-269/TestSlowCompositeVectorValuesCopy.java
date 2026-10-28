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
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnByteVectorField;
import org.apache.lucene.document.KnnFloat16VectorField;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * copy() of the merged vector values of SlowCompositeCodecReaderWrapper (the view that merges and
 * addIndexes(CodecReader...) pass to OneMerge#reorder) when one wrapped segment has no vectors for
 * the field. Package-private access, as in TestGetVectorCountCodecWrappers.
 */
public class TestSlowCompositeVectorValuesCopy extends LuceneTestCase {

  private static final int DOCS_PER_SEGMENT = 3;

  private static void addVector(Document doc, String field, int id) {
    switch (field) {
      case "f" -> doc.add(new KnnFloatVectorField(field, new float[] {id, 1}));
      case "b" -> doc.add(new KnnByteVectorField(field, new byte[] {(byte) id, 1}));
      case "h" ->
          doc.add(
              new KnnFloat16VectorField(
                  field,
                  new short[] {Float.floatToFloat16(id), Float.floatToFloat16(1)},
                  VectorSimilarityFunction.EUCLIDEAN));
      default -> throw new AssertionError(field);
    }
  }

  private static KnnVectorValues values(CodecReader reader, String field) throws IOException {
    return switch (field) {
      case "f" -> reader.getFloatVectorValues(field);
      case "b" -> reader.getByteVectorValues(field);
      default -> reader.getFloat16VectorValues(field);
    };
  }

  private static String vectorString(KnnVectorValues values, int ord) throws IOException {
    return switch (values) {
      case FloatVectorValues v -> Arrays.toString(v.vectorValue(ord));
      case ByteVectorValues v -> Arrays.toString(v.vectorValue(ord));
      case Float16VectorValues v -> Arrays.toString(v.vectorValue(ord));
      default -> throw new AssertionError(values);
    };
  }

  /** Two segments of 3 documents; {@code fieldless} (0 or 1, or -1 for none) has no vectors. */
  private void check(String field, int fieldless) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int seg = 0; seg < 2; seg++) {
          for (int i = 0; i < DOCS_PER_SEGMENT; i++) {
            int id = seg * DOCS_PER_SEGMENT + i;
            Document doc = new Document();
            doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
            if (seg != fieldless) {
              addVector(doc, field, id);
            }
            w.addDocument(doc);
          }
          w.commit(); // one segment per loop
        }
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(2, r.leaves().size());
        List<CodecReader> leaves = new ArrayList<>();
        for (LeafReaderContext ctx : r.leaves()) {
          leaves.add((CodecReader) ctx.reader());
        }
        CodecReader merged = SlowCompositeCodecReaderWrapper.wrap(leaves);
        KnnVectorValues values = values(merged, field);
        KnnVectorValues copy = values.copy();
        assertEquals(values.size(), copy.size());
        // the copy iterates the same documents and returns the same vectors
        KnnVectorValues.DocIndexIterator it = values.iterator();
        KnnVectorValues.DocIndexIterator copyIt = copy.iterator();
        for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
          assertEquals(doc, copyIt.nextDoc());
          assertEquals(vectorString(values, it.index()), vectorString(copy, copyIt.index()));
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, copyIt.nextDoc());
      }
    }
  }

  public void testFloatCopyAllSegmentsHaveVectors() throws IOException {
    check("f", -1);
  }

  public void testFloatCopyFirstSegmentWithoutVectors() throws IOException {
    check("f", 0);
  }

  public void testFloatCopyLastSegmentWithoutVectors() throws IOException {
    check("f", 1);
  }

  public void testByteCopySegmentWithoutVectors() throws IOException {
    check("b", 1);
  }

  public void testFloat16CopySegmentWithoutVectors() throws IOException {
    check("h", 1);
  }
}
