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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.Monster;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

@Monster("buffers about 2.2 GB of doc-values updates; needs -Xmx3g")
public class TestDeleteLostAfterFailedAddDocument extends LuceneTestCase {

  public void testDeleteSurvivesFailedAddDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      w.addDocument(doc("buffered"));
      Term hot = new Term("id", new BytesRef(new byte[1024]));
      for (int i = 0; i < 2_200_000; i++) {
        w.updateNumericDocValue(hot, "counter", i);
      }
      w.deleteDocuments(new Term("id", "buffered"));
      expectThrows(ArithmeticException.class, () -> w.addDocument(doc("next")));
      if (w.isOpen()) {
        w.addDocument(doc("later"));
        w.commit();
        try (DirectoryReader r = DirectoryReader.open(dir)) {
          // expected: the open writer's commit includes the earlier delete (0 hits); actual: 1 hit
          assertEquals(0, new IndexSearcher(r).count(new TermQuery(new Term("id", "buffered"))));
        }
      }
    }
  }

  private static Document doc(String id) {
    Document doc = new Document();
    doc.add(new StringField("id", id, Field.Store.NO));
    doc.add(new NumericDocValuesField("counter", 0));
    return doc;
  }
}
