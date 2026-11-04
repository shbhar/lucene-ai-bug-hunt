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
import java.util.Iterator;
import org.apache.lucene.codecs.FieldsProducer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexImpactsAdvance extends LuceneTestCase {

  public void testBrokenImpactsAdvanceIsReported() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 200; i++) {
          Document doc = new Document();
          doc.add(new TextField("text", "t", Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        CodecReader broken =
            new FilterCodecReader((CodecReader) getOnlyLeafReader(reader)) {
              @Override
              public FieldsProducer getPostingsReader() {
                FieldsProducer fields = in.getPostingsReader();
                return new FieldsProducer() {
                  @Override
                  public Iterator<String> iterator() {
                    return fields.iterator();
                  }

                  @Override
                  public int size() {
                    return fields.size();
                  }

                  @Override
                  public Terms terms(String field) {
                    return new FilterLeafReader.FilterTerms(fields.terms(field)) {
                      @Override
                      public TermsEnum iterator() throws IOException {
                        return new FilterLeafReader.FilterTermsEnum(in.iterator()) {
                          @Override
                          public ImpactsEnum impacts(int flags) throws IOException {
                            return new SlowImpactsEnum(
                                new FilterLeafReader.FilterPostingsEnum(in.postings(null, flags)) {
                                  @Override
                                  public int advance(int target) throws IOException {
                                    int doc = in.advance(target);
                                    return doc == DocIdSetIterator.NO_MORE_DOCS
                                        ? doc
                                        : in.nextDoc();
                                  }
                                });
                          }
                        };
                      }
                    };
                  }

                  @Override
                  public void checkIntegrity(MergePolicy.OneMerge merge) throws IOException {
                    fields.checkIntegrity(merge);
                  }

                  @Override
                  public void close() throws IOException {
                    fields.close();
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
        // expected: "Impacts do not advance to the same document as postings"; actual: no error
        assertNotNull(CheckIndex.testPostings(broken, null).error);
      }
    }
  }
}
