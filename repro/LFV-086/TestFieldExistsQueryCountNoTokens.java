/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
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
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFieldExistsQueryCountNoTokens extends LuceneTestCase {

  public void testCountIncludesValueWithNoTokens() throws Exception {
    FieldType textWithoutNorms = new FieldType(TextField.TYPE_NOT_STORED);
    textWithoutNorms.setOmitNorms(true);
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String title : new String[] {"", "hello"}) { // "" analyzes to no tokens
        Document doc = new Document();
        doc.add(new Field("title", title, textWithoutNorms));
        doc.add(new SortedDocValuesField("title", new BytesRef(title)));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new FieldExistsQuery("title");
        assertEquals(2, searcher.search(query, 10).scoreDocs.length);
        assertEquals(2, searcher.count(query));
      }
    }
  }
}
