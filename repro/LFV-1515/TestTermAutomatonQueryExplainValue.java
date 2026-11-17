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
package org.apache.lucene.sandbox.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTermAutomatonQueryExplainValue extends LuceneTestCase {

  // Expected: the explanation's value is the hit's score (0.13076457). Actual: 0.028268427.
  public void testExplainValueIsTheScore() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new TextField("field", "wifi", Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        IndexSearcher searcher = new IndexSearcher(r);

        TermAutomatonQuery q = new TermAutomatonQuery("field");
        int start = q.createState();
        int middle = q.createState();
        int end = q.createState();
        q.setAccept(end, true);
        q.addTransition(start, end, "wifi");
        q.addTransition(start, middle, "wi");
        q.addTransition(middle, end, "fi");
        q.finish();

        ScoreDoc hit = searcher.search(q, 1).scoreDocs[0];
        assertEquals(hit.score, searcher.explain(q, hit.doc).getValue().floatValue(), 0f);
      }
    }
  }
}
