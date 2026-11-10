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
package org.apache.lucene.queries.function;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQueryValueSourceIOException extends LuceneTestCase {

  public void testReadFailureSurfacesAsIOException() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader failing =
            new FilterLeafReader(getOnlyLeafReader(reader)) {
              @Override
              public Terms terms(String field) {
                return new FilterTerms(super.terms(field)) {
                  @Override
                  public TermsEnum iterator() throws IOException {
                    return new FilterTermsEnum(super.iterator()) {
                      @Override
                      public PostingsEnum postings(PostingsEnum reuse, int flags)
                          throws IOException {
                        throw new IOException("simulated read failure");
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
        IndexSearcher searcher = new IndexSearcher(failing);
        FunctionQuery query =
            new FunctionQuery(new QueryValueSource(new TermQuery(new Term("f", "a")), 0f));
        // expected: the declared IOException; actual: a RuntimeException wrapping it
        expectThrows(IOException.class, () -> searcher.search(query, 10));
      }
    }
  }
}
