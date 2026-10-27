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
import org.apache.lucene.document.SortedSetDocValuesField;
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
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Query-time join (JoinUtil.createJoinQuery over a single-valued from field): two "from" docs share
 * the join value "x" and score 0 and 2. The to-doc score must be the min / average of {0, 2}. The
 * single-valued TermsWithScoreCollector uses an accumulated score of exactly 0.0 as its "first
 * occurrence" marker, so a from-doc that scored 0 is forgotten.
 */
public class TestJoinZeroScoreAggregation extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      Document from1 = new Document(); // scores 0: matches only the FILTER clause
      from1.add(new StringField("side", "from", Field.Store.NO));
      from1.add(new SortedDocValuesField("join", new BytesRef("x")));
      from1.add(new SortedSetDocValuesField("joinMV", new BytesRef("x")));
      w.addDocument(from1);
      Document from2 = new Document(); // scores 2: also matches the boosted SHOULD clause
      from2.add(new StringField("side", "from", Field.Store.NO));
      from2.add(new StringField("tag", "hot", Field.Store.NO));
      from2.add(new SortedDocValuesField("join", new BytesRef("x")));
      from2.add(new SortedSetDocValuesField("joinMV", new BytesRef("x")));
      w.addDocument(from2);
      Document to = new Document();
      to.add(new StringField("side", "to", Field.Store.NO));
      to.add(new StringField("ref", "x", Field.Store.NO));
      w.addDocument(to);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static Query fromQuery() {
    return new BooleanQuery.Builder()
        .add(new BoostQuery(new ConstantScoreQuery(new TermQuery(new Term("tag", "hot"))), 2f), Occur.SHOULD)
        .add(new TermQuery(new Term("side", "from")), Occur.FILTER)
        .build();
  }

  private float joinScore(String fromField, boolean multi, ScoreMode sm) throws Exception {
    // sanity: the two from docs score exactly 0 and 2
    TopDocs from = searcher.search(fromQuery(), 10);
    assertEquals(2, from.totalHits.value());
    assertEquals(2f, from.scoreDocs[0].score, 0f);
    assertEquals(0f, from.scoreDocs[1].score, 0f);
    Query join = JoinUtil.createJoinQuery(fromField, multi, "ref", fromQuery(), searcher, sm);
    TopDocs td = searcher.search(join, 10);
    assertEquals(1, td.totalHits.value());
    return td.scoreDocs[0].score;
  }

  public void testMinSingleValued() throws Exception {
    assertEquals("multi-valued collector", 0f, joinScore("joinMV", true, ScoreMode.Min), 0f);
    assertEquals("single-valued collector", 0f, joinScore("join", false, ScoreMode.Min), 0f);
  }

  public void testAvgSingleValued() throws Exception {
    assertEquals("multi-valued collector", 1f, joinScore("joinMV", true, ScoreMode.Avg), 0f);
    assertEquals("single-valued collector", 1f, joinScore("join", false, ScoreMode.Avg), 0f);
  }
}
