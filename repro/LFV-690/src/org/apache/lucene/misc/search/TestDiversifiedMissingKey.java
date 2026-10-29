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
package org.apache.lucene.misc.search;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.DocValues;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDiversifiedMissingKey extends LuceneTestCase {

  public void testDocWithoutKeyDoesNotUseKeyZeroQuota() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document());
      Document seller0 = new Document();
      seller0.add(new NumericDocValuesField("seller", 0));
      w.addDocument(seller0);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        TopDocs td =
            new IndexSearcher(reader)
                .search(
                    MatchAllDocsQuery.INSTANCE,
                    new DiversifiedTopDocsCollectorManager<DiversifiedTopDocsCollector>(2, 1) {
                      @Override
                      public DiversifiedTopDocsCollector newCollector() {
                        return new DiversifiedTopDocsCollector(2, 1) {
                          @Override
                          protected NumericDocValues getKeys(LeafReaderContext context) {
                            try {
                              return DocValues.getNumeric(context.reader(), "seller");
                            } catch (IOException e) {
                              throw new UncheckedIOException(e);
                            }
                          }
                        };
                      }
                    });
        // expected 2 hits (one per group); actual 1: the keyless doc is put in key 0's group
        assertEquals("hits", 2, td.scoreDocs.length);
      }
    }
  }
}
