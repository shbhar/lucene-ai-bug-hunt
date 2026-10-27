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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IntervalQuery(field, source, pivot, exp) has its own copy of the sigmoid arithmetic
 * (IntervalScoreFunction.SigmoidFunction): pivot^exp is precomputed in a double, so when it
 * overflows every matching document scores NaN. pivot and exp are documented as "must be within
 * (0, +Infinity)". Public API only.
 */
public class TestIntervalQuerySigmoidPivotPower extends LuceneTestCase {

  public void testPivotPowerOverflow() throws IOException {
    float pivot = 4f; // sloppy frequency at which the score is 0.5
    float exp = 600f; // 4^600 = 2^1200 overflows a double
    String[] bodies = {"a", "a x a", "a x a x a x a", "a a a a a a a a"};
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String body : bodies) {
          Document doc = new Document();
          doc.add(new TextField("body", body, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query q = new IntervalQuery("body", Intervals.term("a"), pivot, exp);
        TopDocs td = searcher.search(q, 10);
        assertEquals(bodies.length, td.scoreDocs.length);
        StringBuilder sb = new StringBuilder("pivot=" + pivot + " exp=" + exp + ":");
        for (ScoreDoc sd : td.scoreDocs) sb.append(" doc=").append(sd.doc).append(" score=").append(sd.score);
        for (ScoreDoc sd : td.scoreDocs) {
          assertTrue("hit with an invalid doc id; " + sb, sd.doc >= 0 && sd.doc < reader.maxDoc());
        }
        for (ScoreDoc sd : td.scoreDocs) {
          assertFalse("NaN score; " + sb, Float.isNaN(sd.score));
          assertTrue("score outside [0, 1]; " + sb, sd.score >= 0 && sd.score <= 1);
        }
        // sloppy freqs are 1, 2, 4 and 8 (one-term intervals): expected scores ~0, ~0, 0.5, ~1
        assertEquals(sb.toString(), 3, td.scoreDocs[0].doc);
        assertEquals(sb.toString(), 2, td.scoreDocs[1].doc);
        assertEquals(sb.toString(), 0.5f, td.scoreDocs[1].score, 1e-6f);
      }
    }
  }
}
