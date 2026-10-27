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
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Query#createWeight receives "the boost that is propagated by the parent queries"; TestJoinUtil's
 * checkBoost asserts that a BoostQuery(join, 10) scores 10x the join for string-field and
 * global-ordinal joins. The numeric join (PointInSetIncludingScoreQuery) is not covered and ignores
 * the boost.
 */
public class TestNumericScoreJoinBoost extends LuceneTestCase {

  private static float topScore(IndexSearcher searcher, Query q) throws Exception {
    TopDocs td = searcher.search(q, 1);
    assertEquals(1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  public void testBoostIsApplied() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document from = new Document();
        from.add(new TextField("body", "x y", Field.Store.NO));
        from.add(new NumericDocValuesField("fromId", 7));
        from.add(new SortedDocValuesField("fromKey", new BytesRef("7")));
        w.addDocument(from);
        Document to = new Document();
        to.add(new IntPoint("toId", 7));
        to.add(new StringField("toKey", "7", Field.Store.NO));
        w.addDocument(to);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query fromQuery = new TermQuery(new Term("body", "x"));
        for (ScoreMode scoreMode : new ScoreMode[] {ScoreMode.Max, ScoreMode.Total, ScoreMode.Avg, ScoreMode.Min}) {
          // control: the string-field join honours the boost
          Query stringJoin =
              JoinUtil.createJoinQuery("fromKey", false, "toKey", fromQuery, searcher, scoreMode);
          float s = topScore(searcher, stringJoin);
          assertEquals(10 * s, topScore(searcher, new BoostQuery(stringJoin, 10)), 1e-5f);

          Query numericJoin =
              JoinUtil.createJoinQuery(
                  "fromId", false, "toId", Integer.class, fromQuery, searcher, scoreMode);
          float n = topScore(searcher, numericJoin);
          assertEquals("numeric join " + scoreMode + " score=" + n, s, n, 1e-5f);
          assertEquals(
              "numeric join " + scoreMode + ": BoostQuery(join, 10) must score 10x the join",
              10 * n,
              topScore(searcher, new BoostQuery(numericJoin, 10)),
              1e-5f);
        }
      }
    }
  }
}
