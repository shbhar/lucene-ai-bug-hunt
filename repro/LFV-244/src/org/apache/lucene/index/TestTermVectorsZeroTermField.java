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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestTermVectorsZeroTermField extends LuceneTestCase {

  public void testOtherDocumentOfChunkReadsBack() throws Exception {
    FieldType offsets = new FieldType(TextField.TYPE_NOT_STORED);
    offsets.setStoreTermVectors(true);
    offsets.setStoreTermVectorOffsets(true);
    FieldType plain = new FieldType(TextField.TYPE_NOT_STORED);
    plain.setStoreTermVectors(true);
    try (Directory src = new ByteBuffersDirectory();
        Directory dst = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig())) {
        Document d0 = new Document();
        d0.add(new Field("a", "x", offsets));
        w.addDocument(d0);
        Document d1 = new Document();
        d1.add(new Field("b", "y", plain));
        w.addDocument(d1);
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          IndexWriter w =
              new IndexWriter(dst, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
        LeafReader emptyVectorA =
            new FilterLeafReader(getOnlyLeafReader(r)) {
              @Override
              public TermVectors termVectors() throws IOException {
                TermVectors tv = in.termVectors();
                return new TermVectors() {
                  @Override
                  public Fields get(int doc) throws IOException {
                    return new FilterFields(tv.get(doc)) {
                      @Override
                      public Terms terms(String field) {
                        Terms t = in.terms(field);
                        if (field.equals("a") == false) {
                          return t;
                        }
                        return new FilterTerms(t) {
                          @Override
                          public TermsEnum iterator() {
                            return TermsEnum.EMPTY;
                          }

                          @Override
                          public long size() {
                            return 0;
                          }

                          @Override
                          public long getSumTotalTermFreq() {
                            return 0;
                          }

                          @Override
                          public long getSumDocFreq() {
                            return 0;
                          }

                          @Override
                          public int getDocCount() {
                            return 0;
                          }
                        };
                      }
                    };
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
            };
        w.addIndexes(SlowCodecReaderWrapper.wrap(emptyVectorA));
      }
      try (DirectoryReader r = DirectoryReader.open(dst)) {
        String doc1;
        try {
          doc1 = r.termVectors().get(1, "b").iterator().next().utf8ToString();
        } catch (IOException e) {
          doc1 = e.toString();
        }
        // expected: doc 1's term vector "y" reads back; actual: IOException "offset 0 is invalid"
        assertEquals("y", doc1);
      }
    }
  }
}
