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
package org.apache.lucene.codecs.perfield;

import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.DocValuesFormat;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.KnnVectorsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FloatVectorValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A per-field doc-values or vectors hook that returns another codec's (per-field) format is
 * accepted, the segment is written and committed, but the committed index cannot be opened.
 * PerFieldPostingsFormat rejects the same configuration up front ("cannot embed
 * PerFieldPostingsFormat inside itself").
 */
public class TestPerFieldNestedFormat extends LuceneTestCase {

  public void testDocValuesFormatOfAnotherCodec() throws Exception {
    Codec base = TestUtil.getDefaultCodec();
    // the default codec's doc values format is itself a PerFieldDocValuesFormat
    DocValuesFormat nested = TestUtil.getDefaultCodec().docValuesFormat();
    Codec codec =
        new FilterCodec(base.getName(), base) {
          final DocValuesFormat perField =
              new PerFieldDocValuesFormat() {
                @Override
                public DocValuesFormat getDocValuesFormatForField(String field) {
                  return nested;
                }
              };

          @Override
          public DocValuesFormat docValuesFormat() {
            return perField;
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("n", 5));
        w.addDocument(doc);
      } catch (IllegalStateException | IllegalArgumentException rejected) {
        return; // rejecting the configuration up front is fine, as PerFieldPostingsFormat does
      }
      // the writer accepted and committed the document: the index must be readable
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = r.leaves().get(0).reader();
        NumericDocValues values = leaf.getNumericDocValues("n");
        assertNotNull(values);
        assertEquals(0, values.nextDoc());
        assertEquals(5, values.longValue());
      }
    }
  }

  public void testKnnVectorsFormatOfAnotherCodec() throws Exception {
    Codec base = TestUtil.getDefaultCodec();
    // the default codec's vectors format is itself a PerFieldKnnVectorsFormat
    KnnVectorsFormat nested = TestUtil.getDefaultCodec().knnVectorsFormat();
    Codec codec =
        new FilterCodec(base.getName(), base) {
          final KnnVectorsFormat perField =
              new PerFieldKnnVectorsFormat() {
                @Override
                public KnnVectorsFormat getKnnVectorsFormatForField(String field) {
                  return nested;
                }
              };

          @Override
          public KnnVectorsFormat knnVectorsFormat() {
            return perField;
          }
        };
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setCodec(codec))) {
        Document doc = new Document();
        doc.add(new KnnFloatVectorField("v", new float[] {1f, 2f}));
        w.addDocument(doc);
      } catch (IllegalStateException | IllegalArgumentException rejected) {
        return; // rejecting the configuration up front would be fine
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader leaf = r.leaves().get(0).reader();
        FloatVectorValues values = leaf.getFloatVectorValues("v");
        assertNotNull(values);
        assertEquals(1, values.size());
      }
    }
  }
}
