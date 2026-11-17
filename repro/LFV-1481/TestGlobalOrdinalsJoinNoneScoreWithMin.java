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
package org.apache.lucene.search.join;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestGlobalOrdinalsJoinNoneScoreWithMin extends LuceneTestCase {

  // Expected: the min filter leaves the score of a ScoreMode.None hit unchanged (1.0); actual: 0.0.
  public void testMinFilterDoesNotChangeScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
        writer.addDocument(doc("to"));
        writer.addDocument(doc("from"));
        writer.addDocument(doc("from"));
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query fromQuery = new TermQuery(new Term("type", "from"));
        Query toQuery = new TermQuery(new Term("type", "to"));

        Query unfiltered =
            JoinUtil.createJoinQuery(
                "join", fromQuery, toQuery, searcher, ScoreMode.None, null, 0, Integer.MAX_VALUE);
        Query atLeastTwoFrom =
            JoinUtil.createJoinQuery(
                "join", fromQuery, toQuery, searcher, ScoreMode.None, null, 2, Integer.MAX_VALUE);

        assertEquals(
            searcher.search(unfiltered, 1).scoreDocs[0].score,
            searcher.search(atLeastTwoFrom, 1).scoreDocs[0].score,
            0f);
      }
    }
  }

  private static Document doc(String type) {
    Document doc = new Document();
    doc.add(new StringField("type", type, Field.Store.NO));
    doc.add(new SortedDocValuesField("join", new BytesRef("family")));
    return doc;
  }
}
