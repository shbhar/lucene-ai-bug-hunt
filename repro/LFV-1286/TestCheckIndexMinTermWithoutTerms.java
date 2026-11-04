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
import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestCheckIndexMinTermWithoutTerms extends LuceneTestCase {

  // expected: field "b" (min term but no terms) is reported; actual: status.error is null
  public void testLaterFieldWithMinTermButNoTerms() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(
          List.of(
              new StringField("a", "x", Field.Store.NO),
              new StringField("b", "y", Field.Store.NO)));
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader broken =
            new FilterLeafReader(getOnlyLeafReader(r)) {
              @Override
              public Terms terms(String field) {
                Terms t = super.terms(field);
                if (field.equals("b") == false) {
                  return t;
                }
                return new FilterTerms(t) {
                  @Override
                  public TermsEnum iterator() {
                    return TermsEnum.EMPTY;
                  }

                  @Override
                  public BytesRef getMin() throws IOException {
                    return in.getMin();
                  }

                  @Override
                  public BytesRef getMax() throws IOException {
                    return in.getMax();
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

              @Override
              public CacheHelper getCoreCacheHelper() {
                return null;
              }

              @Override
              public CacheHelper getReaderCacheHelper() {
                return null;
              }
            };
        CheckIndex.Status.TermIndexStatus status =
            CheckIndex.testPostings(SlowCodecReaderWrapper.wrap(broken), null);
        assertNotNull(status.error);
        assertTrue(
            status.error.getMessage(),
            status
                .error
                .getMessage()
                .contains("field=\"b\": minTerm is non-null yet we saw no terms"));
      }
    }
  }
}
