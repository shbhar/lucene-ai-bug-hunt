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

import java.util.List;
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
import org.apache.lucene.search.DisjunctionMaxQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A numeric join with a ScoreMode other than None scores each "to" document with the aggregated
 * score of the matching "from" documents. Two such joins over two different "from" indexes give
 * different scores, but they compare equal, so DisjunctionMaxQuery (and BooleanQuery) de-duplicate
 * them into one clause.
 * The string-field join (TermsIncludingScoreQuery) keeps the "from" reader in its equals and does
 * not have this problem.
 */
public class TestNumericScoreJoinEquality extends LuceneTestCase {

  private static Directory fromIndex(String text) throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document d = new Document();
      d.add(new TextField("body", text, Field.Store.NO));
      d.add(new NumericDocValuesField("fromId", 7));
      d.add(new SortedDocValuesField("fromKey", new BytesRef("7")));
      w.addDocument(d);
      Document other = new Document(); // keeps the statistics of "x" non-trivial
      other.add(new TextField("body", "y z", Field.Store.NO));
      w.addDocument(other);
    }
    return dir;
  }

  private static float score(IndexSearcher searcher, Query q) throws Exception {
    TopDocs td = searcher.search(q, 1);
    assertEquals(1, td.scoreDocs.length);
    return td.scoreDocs[0].score;
  }

  public void testJoinsOverDifferentFromIndexes() throws Exception {
    try (Directory fromA = fromIndex("x");
        Directory fromB = fromIndex("x x x y y y y y y y y");
        Directory to = newDirectory()) {
      try (IndexWriter w = new IndexWriter(to, new IndexWriterConfig())) {
        Document d = new Document();
        d.add(new IntPoint("toId", 7));
        d.add(new StringField("toKey", "7", Field.Store.NO));
        w.addDocument(d);
      }
      try (DirectoryReader ra = DirectoryReader.open(fromA);
          DirectoryReader rb = DirectoryReader.open(fromB);
          DirectoryReader rt = DirectoryReader.open(to)) {
        IndexSearcher sa = new IndexSearcher(ra);
        IndexSearcher sb = new IndexSearcher(rb);
        IndexSearcher st = new IndexSearcher(rt);
        Query from = new TermQuery(new Term("body", "x"));

        // numeric join (PointInSetIncludingScoreQuery)
        Query qa = JoinUtil.createJoinQuery("fromId", false, "toId", Integer.class, from, sa, ScoreMode.Max);
        Query qb = JoinUtil.createJoinQuery("fromId", false, "toId", Integer.class, from, sb, ScoreMode.Max);
        float scoreA = score(st, qa);
        float scoreB = score(st, qb);
        assertTrue("precondition: the two joins score differently", scoreA != scoreB);

        // the same join on a string key (TermsIncludingScoreQuery) for comparison
        Query ta = JoinUtil.createJoinQuery("fromKey", false, "toKey", from, sa, ScoreMode.Max);
        Query tb = JoinUtil.createJoinQuery("fromKey", false, "toKey", from, sb, ScoreMode.Max);
        assertFalse("string join: joins over different from indexes are not equal", ta.equals(tb));

        // a disjunction-max of the two numeric joins must score the better of the two
        Query best = new DisjunctionMaxQuery(List.of(qa, qb), 0f);
        float max = score(st, best);
        assertEquals(
            "scoreA="
                + scoreA
                + " scoreB="
                + scoreB
                + " qa.equals(qb)="
                + qa.equals(qb)
                + " qa.hashCode()==qb.hashCode() "
                + (qa.hashCode() == qb.hashCode())
                + " rewritten="
                + st.rewrite(best),
            Math.max(scoreA, scoreB),
            max,
            1e-5f);
      }
    }
  }
}
