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
package org.apache.lucene.codecs.bitvectors;

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * HnswBitVectorsFormat rejects non-BYTE vector fields with an IllegalArgumentException
 * (TestHnswBitVectorsFormat#testFloatVectorFails expects exactly that from addDocument). The check
 * runs in KnnVectorsWriter#addField, which IndexingChain treats as an aborting step, so the
 * rejected document closes the IndexWriter and discards every document added since the last
 * commit.
 *
 * <p>Only public API is used.
 */
public class TestBitVectorsFloatFieldClosesWriter extends LuceneTestCase {

  public void testRejectedFloatVectorKeepsWriterUsable() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.alwaysKnnVectorsFormat(new HnswBitVectorsFormat()));
      String observed;
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(List.of(new StringField("id", "0", Field.Store.YES)));
        w.commit();
        w.addDocument(List.of(new StringField("id", "1", Field.Store.YES)));

        // Rejected, as documented by the format's own test.
        IllegalArgumentException e =
            expectThrows(
                IllegalArgumentException.class,
                () ->
                    w.addDocument(
                        List.of(
                            new KnnFloatVectorField(
                                "v", new float[] {1, 2, 3, 4}, VectorSimilarityFunction.DOT_PRODUCT))));
        assertTrue(e.getMessage(), e.getMessage().contains("only supports BYTE encoding"));

        // Record what a rejected document did to the writer, then check everything at once.
        String tragic = String.valueOf(w.getTragicException());
        boolean open = w.isOpen();
        String nextAdd;
        try {
          w.addDocument(List.of(new StringField("id", "2", Field.Store.YES)));
          w.commit();
          nextAdd = "ok";
        } catch (Exception ex) {
          nextAdd = ex.getClass().getSimpleName();
        }
        observed = "tragic=" + tragic + ", open=" + open + ", next addDocument+commit=" + nextAdd;
      }
      int committed;
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        committed = r.numDocs();
      }
      // A rejected document must not close the writer or drop the uncommitted document "1".
      assertEquals(
          "tragic=null, open=true, next addDocument+commit=ok, committed docs=3",
          observed + ", committed docs=" + committed);
    }
  }
}
