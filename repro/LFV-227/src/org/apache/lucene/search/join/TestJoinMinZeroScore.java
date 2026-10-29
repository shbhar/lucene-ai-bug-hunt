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
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestJoinMinZeroScore extends LuceneTestCase {

  public void testMinKeepsZeroScore() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (boolean hot : new boolean[] {false, true}) {
          Document from = new Document();
          from.add(new StringField("tag", "from", Field.Store.NO));
          if (hot) {
            from.add(new StringField("tag", "hot", Field.Store.NO));
          }
          from.add(new SortedDocValuesField("join", new BytesRef("x")));
          w.addDocument(from);
        }
        Document to = new Document();
        to.add(new StringField("ref", "x", Field.Store.NO));
        w.addDocument(to);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query hot = new ConstantScoreQuery(new TermQuery(new Term("tag", "hot")));
        Query fromQuery =
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term("tag", "from")), Occur.FILTER)
                .add(new BoostQuery(hot, 2f), Occur.SHOULD)
                .build();
        Query join =
            JoinUtil.createJoinQuery("join", false, "ref", fromQuery, searcher, ScoreMode.Min);
        // from scores [0, 2] for join value x: expected min 0.0, actual 2.0
        assertEquals(0f, searcher.search(join, 1).scoreDocs[0].score, 0f);
      }
    }
  }
}
