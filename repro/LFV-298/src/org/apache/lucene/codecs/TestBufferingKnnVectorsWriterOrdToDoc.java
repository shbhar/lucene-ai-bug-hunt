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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.ByteVectorValues;
import org.apache.lucene.index.FieldInfo;
import org.apache.lucene.index.Float16VectorValues;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.KnnVectorValues;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestBufferingKnnVectorsWriterOrdToDoc extends LuceneTestCase {

  public void testSparseFieldOrdToDoc() throws Exception {
    List<String> seen = new ArrayList<>();
    KnnVectorsFormat format =
        new KnnVectorsFormat("Probe") {
          @Override
          public KnnVectorsWriter fieldsWriter(SegmentWriteState state) {
            return new BufferingKnnVectorsWriter() {
              @Override
              protected void writeField(FieldInfo info, FloatVectorValues values, int maxDoc)
                  throws IOException {
                KnnVectorValues.DocIndexIterator it = values.iterator();
                for (int doc = it.nextDoc();
                    doc != DocIdSetIterator.NO_MORE_DOCS;
                    doc = it.nextDoc()) {
                  seen.add(doc + "->" + values.ordToDoc(it.index()));
                }
              }

              @Override
              protected void writeField(FieldInfo info, Float16VectorValues values, int maxDoc) {}

              @Override
              protected void writeField(FieldInfo info, ByteVectorValues values, int maxDoc) {}

              @Override
              public void finish() {}

              @Override
              public void close() {}
            };
          }

          @Override
          public KnnVectorsReader fieldsReader(SegmentReadState state) {
            throw new UnsupportedOperationException();
          }

          @Override
          public int getMaxDimensions(String fieldName) {
            return 1024;
          }
        };
    Codec base = TestUtil.getDefaultCodec();
    Codec codec =
        new FilterCodec(base.getName(), base) {
          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return format;
          }
        };
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
      for (int d = 0; d < 4; d++) {
        Document doc = new Document();
        if (d != 1) {
          doc.add(new KnnFloatVectorField("v", new float[] {d}));
        }
        w.addDocument(doc);
      }
      w.commit();
    }
    // docID->ordToDoc(index()) at flush: expected each ordinal to map to its own doc, actual 2->1, 3->2
    assertEquals(List.of("0->0", "2->2", "3->3"), seen);
  }
}
