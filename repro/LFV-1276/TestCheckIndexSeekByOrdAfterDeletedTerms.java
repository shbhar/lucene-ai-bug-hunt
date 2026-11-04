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
import org.apache.lucene.codecs.blocktreeords.BlockTreeOrdsPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckIndexSeekByOrdAfterDeletedTerms extends LuceneTestCase {

  public void testSeekByOrdIsCheckedAfterEarlierFieldTermsAreDeleted() throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig()
              .setCodec(TestUtil.alwaysPostingsFormat(new BlockTreeOrdsPostingsFormat()))
              .setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc0 = new Document();
        doc0.add(new StringField("a", "a0", Field.Store.NO));
        doc0.add(new StringField("a", "a1", Field.Store.NO));
        w.addDocument(doc0);
        Document doc1 = new Document();
        doc1.add(new StringField("b", "b0", Field.Store.NO));
        doc1.add(new StringField("b", "b1", Field.Store.NO));
        w.addDocument(doc1);
        w.deleteDocuments(new Term("a", "a0"));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader broken =
            new FilterLeafReader(getOnlyLeafReader(r)) {
              @Override
              public Terms terms(String field) {
                Terms terms = super.terms(field);
                if (field.equals("b") == false) {
                  return terms;
                }
                return new FilterTerms(terms) {
                  @Override
                  public TermsEnum iterator() throws IOException {
                    return new FilterTermsEnum(in.iterator()) {
                      @Override
                      public void seekExact(long ord) throws IOException {
                        in.seekExact(ord == 0 ? 1 : ord);
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
        // expected: "seek to ord 0 returned ord 1" for field b; actual on main: no error
        assertNotNull(CheckIndex.testPostings(SlowCodecReaderWrapper.wrap(broken), null).error);
      }
    }
  }
}
