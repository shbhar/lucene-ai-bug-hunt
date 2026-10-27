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
package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IntervalQuery.equals/hashCode ignore the scoring function (pivot / exponent), although
 * IntervalScoreFunction declares equals/hashCode. Two IntervalQuery objects that score differently
 * are therefore equal, and BooleanQuery.rewrite merges them as duplicate SHOULD clauses, which
 * changes the score of every matching document.
 */
public class TestIntervalQueryEqualsIgnoresScoreFunction extends LuceneTestCase {

  public void testDifferentPivotsAreEqual() {
    Query q1 = new IntervalQuery("f", Intervals.term("a"), 1f);
    Query q2 = new IntervalQuery("f", Intervals.term("a"), 100f);
    assertFalse("queries with different scoring pivots must not be equal", q1.equals(q2));
  }

  public void testDisjunctionOfDifferentPivotsScoresAsSum() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "a b a c a", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        Query q1 = new IntervalQuery("f", Intervals.term("a"), 1f);
        Query q2 = new IntervalQuery("f", Intervals.term("a"), 100f);
        float s1 = searcher.search(q1, 1).scoreDocs[0].score;
        float s2 = searcher.search(q2, 1).scoreDocs[0].score;
        assertTrue("the two pivots must give different scores", s1 != s2);

        Query both = new BooleanQuery.Builder().add(q1, Occur.SHOULD).add(q2, Occur.SHOULD).build();
        TopDocs td = searcher.search(both, 1);
        // A disjunction scores the sum of its matching clauses.
        assertEquals(
            "BooleanQuery(q1 OR q2) must score s1 + s2 (s1=" + s1 + ", s2=" + s2 + "), rewritten to "
                + searcher.rewrite(both),
            s1 + s2,
            td.scoreDocs[0].score,
            1e-5f * (s1 + s2));
      }
    }
  }
}
