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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCheckTopScoresMinShouldMatch extends LuceneTestCase {

  public void testMinShouldMatch() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String text : new String[] {"c", "a b"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("f", "a")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("f", "b")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term("f", "c")), BooleanClause.Occur.SHOULD)
                .setMinimumNumberShouldMatch(2)
                .build();
        // expected: pass (doc 1 is the only match, same score in both modes); actual: fails
        CheckHits.checkTopScores(random(), query, new IndexSearcher(reader));
      }
    }
  }
}
