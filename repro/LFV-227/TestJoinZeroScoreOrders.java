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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.SortedSetDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.ConstantScoreQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Several join values, each with its from-document scores in index (= collection) order. For every
 * ScoreMode the single-valued join must give the same per-join-value score as the multi-valued join
 * and as the ScoreMode javadoc ("the min / average / max / sum of all child scores").
 */
public class TestJoinZeroScoreOrders extends LuceneTestCase {

  private static final int[][] GROUPS = {{0, 2}, {2, 0}, {3, 0, 5}, {0, 0, 5}, {0, 0}};
  private static final int[] POSITIVE_SCORES = {2, 3, 5};

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int g = 0; g < GROUPS.length; g++) {
        for (int score : GROUPS[g]) {
          Document from = new Document();
          from.add(new StringField("side", "from", Field.Store.NO));
          if (score > 0) {
            from.add(new StringField("s", Integer.toString(score), Field.Store.NO));
          }
          from.add(new SortedDocValuesField("join", new BytesRef("g" + g)));
          from.add(new SortedSetDocValuesField("joinMV", new BytesRef("g" + g)));
          w.addDocument(from);
        }
      }
      for (int g = 0; g < GROUPS.length; g++) {
        Document to = new Document();
        to.add(new StringField("side", "to", Field.Store.NO));
        to.add(new StringField("ref", "g" + g, Field.Store.NO));
        to.add(new StoredField("id", g));
        w.addDocument(to);
      }
    }
    reader = DirectoryReader.open(dir);
    assertEquals("one segment, so collection order = index order", 1, reader.leaves().size());
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** side:from as a FILTER, plus one constant-score SHOULD clause per positive score. */
  private static Query fromQuery() {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    b.add(new TermQuery(new Term("side", "from")), Occur.FILTER);
    for (int s : POSITIVE_SCORES) {
      Query clause = new ConstantScoreQuery(new TermQuery(new Term("s", Integer.toString(s))));
      b.add(new BoostQuery(clause, s), Occur.SHOULD);
    }
    return b.build();
  }

  private static float expected(int[] scores, ScoreMode mode) {
    double sum = 0;
    float min = Float.POSITIVE_INFINITY;
    float max = Float.NEGATIVE_INFINITY;
    for (int s : scores) {
      sum += s;
      min = Math.min(min, s);
      max = Math.max(max, s);
    }
    switch (mode) {
      case Min:
        return min;
      case Max:
        return max;
      case Total:
        return (float) sum;
      case Avg:
        return (float) (sum / scores.length);
      case None:
      default:
        throw new AssertionError(mode);
    }
  }

  private static String describe(float[] perGroup) {
    List<String> parts = new ArrayList<>();
    for (int g = 0; g < GROUPS.length; g++) {
      StringBuilder sb = new StringBuilder("[");
      for (int i = 0; i < GROUPS[g].length; i++) {
        sb.append(i == 0 ? "" : ", ").append(GROUPS[g][i]);
      }
      parts.add(sb.append("] -> ").append(perGroup[g]).toString());
    }
    return String.join("; ", parts);
  }

  private float[] join(String fromField, boolean multi, ScoreMode mode) throws Exception {
    // sanity: every from-document scores exactly its intended score
    TopDocs from = searcher.search(fromQuery(), 100);
    Map<Integer, Float> fromScores = new HashMap<>();
    for (ScoreDoc sd : from.scoreDocs) {
      fromScores.put(sd.doc, sd.score);
    }
    int doc = 0;
    for (int[] group : GROUPS) {
      for (int score : group) {
        assertEquals("from doc " + doc, (float) score, fromScores.get(doc), 0f);
        doc++;
      }
    }
    Query join = JoinUtil.createJoinQuery(fromField, multi, "ref", fromQuery(), searcher, mode);
    TopDocs td = searcher.search(join, 100);
    assertEquals(GROUPS.length, td.totalHits.value());
    StoredFields stored = searcher.storedFields();
    float[] perGroup = new float[GROUPS.length];
    for (ScoreDoc sd : td.scoreDocs) {
      perGroup[stored.document(sd.doc).getField("id").numericValue().intValue()] = sd.score;
    }
    return perGroup;
  }

  private void check(ScoreMode mode) throws Exception {
    float[] want = new float[GROUPS.length];
    for (int g = 0; g < GROUPS.length; g++) {
      want[g] = expected(GROUPS[g], mode);
    }
    assertEquals("multi-valued " + mode, describe(want), describe(join("joinMV", true, mode)));
    assertEquals("single-valued " + mode, describe(want), describe(join("join", false, mode)));
  }

  public void testMin() throws Exception {
    check(ScoreMode.Min);
  }

  public void testAvg() throws Exception {
    check(ScoreMode.Avg);
  }

  public void testMaxControl() throws Exception {
    check(ScoreMode.Max);
  }

  public void testTotalControl() throws Exception {
    check(ScoreMode.Total);
  }
}
