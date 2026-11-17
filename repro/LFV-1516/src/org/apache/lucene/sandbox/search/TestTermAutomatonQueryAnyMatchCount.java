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

public class TestTermAutomatonQueryAnyMatchCount extends LuceneTestCase {

  public void testOneMatchScoresAlikeWhateverTokenAnyCrosses() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String text : new String[] {"comes the sun", "comes sun sun"}) {
        Document doc = new Document();
        doc.add(new TextField("field", text, Field.Store.NO));
        w.addDocument(doc);
      }

      TermAutomatonQuery q = new TermAutomatonQuery("field");
      int init = q.createState();
      int s1 = q.createState();
      int s2 = q.createState();
      int s3 = q.createState();
      q.addTransition(init, s1, "comes");
      q.addAnyTransition(s1, s2);
      q.addTransition(s2, s3, "sun");
      q.addTransition(s1, s3, "moon");
      q.setAccept(s3, true);
      q.finish();

      try (DirectoryReader r = DirectoryReader.open(w)) {
        ScoreDoc[] hits = new IndexSearcher(r).search(q, 2).scoreDocs;
        // expected: equal scores (three tokens and one match each); actual: 0.22790195, 0.16574687
        assertEquals(hits[0].score, hits[1].score, 0f);
      }
    }
  }
}
