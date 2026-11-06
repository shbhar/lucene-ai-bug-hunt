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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestTermOrdValMismatchMessage extends LuceneTestCase {

  public void testMessageWhenTermsIndexHasFewerTerms() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String dv : new String[] {"c", "a", "b"}) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        doc.add(new SortedDocValuesField("f", new BytesRef(dv)));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Sort sort = new Sort(new SortField("f", SortField.Type.STRING));
        IllegalStateException e =
            expectThrows(
                IllegalStateException.class,
                () ->
                    new IndexSearcher(reader)
                        .search(new MatchAllDocsQuery(), new TopFieldCollectorManager(sort, 1, 1)));
        // terms index has 1 term, doc values 3; actual: "Terms have more than 1 unique terms while doc values have exactly 1 terms"
        assertFalse(e.getMessage(), e.getMessage().contains("doc values have exactly 1 terms"));
      }
    }
  }
}
