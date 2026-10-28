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
import java.util.Locale;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IntervalQuery's default score function is a saturation with pivot 1; its explanation states
 * the score as "w * S / (S + k)" with S the sloppy frequency, which is positive and strictly
 * increasing in S. The implementation evaluates weight * (1 - pivot / (pivot + S)) in float, which
 * cancels for small S (wide intervals): the score loses its resolution and reaches exactly 0.
 * Public API only.
 */
public class TestIntervalQueryScoreCancellation extends LuceneTestCase {

  /** One document per gap: "a" at position 0 and "b" at position gap. */
  private static DirectoryReader index(Directory dir, int... gaps) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
      for (int gap : gaps) {
        Document doc = new Document();
        doc.add(
            new TextField(
                "f", new CannedTokenStream(new Token("a", 1, 0, 1), new Token("b", gap, 2, 3))));
        doc.add(new StoredField("gap", gap));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return DirectoryReader.open(dir);
  }

  private static float documented(Explanation e) {
    // details: [w, k, S]
    double w = e.getDetails()[0].getValue().doubleValue();
    double k = e.getDetails()[1].getValue().doubleValue();
    double s = e.getDetails()[2].getValue().doubleValue();
    return (float) (w * s / (s + k));
  }

  public void testWideIntervalScoresZeroAndDistinctWidthsTie() throws IOException {
    // gaps of 1,000,000 and 1,030,000 positions (a long document) and 2^25 positions
    int[] gaps = {1_000_000, 1_030_000, 1 << 25};
    try (Directory dir = newDirectory();
        DirectoryReader reader = index(dir, gaps)) {
      IndexSearcher searcher = newSearcher(reader);
      Query q = new IntervalQuery("f", Intervals.unordered(Intervals.term("a"), Intervals.term("b")));
      // indexed by gap number (0, 1, 2), not by doc id: merges may reorder documents
      float[] got = new float[gaps.length];
      float[] expected = new float[gaps.length];
      StringBuilder msg = new StringBuilder();
      ScoreDoc[] hits = searcher.search(q, 10).scoreDocs;
      assertEquals(gaps.length, hits.length);
      for (ScoreDoc sd : hits) {
        Explanation e = searcher.explain(q, sd.doc);
        int gap = searcher.storedFields().document(sd.doc).getField("gap").numericValue().intValue();
        int g = java.util.Arrays.binarySearch(gaps, gap);
        got[g] = sd.score;
        expected[g] = documented(e);
        msg.append(
            String.format(
                Locale.ROOT,
                "%n doc %d (gap %d): S=%s, documented w*S/(S+k) = %s, score = %s",
                sd.doc,
                gap,
                e.getDetails()[2].getValue(),
                expected[g],
                sd.score));
      }
      boolean ok = true;
      for (int g = 0; g < gaps.length; g++) {
        ok &= got[g] > 0f && Math.abs(got[g] - expected[g]) <= 2 * Math.ulp(expected[g]);
      }
      // the narrower interval must outscore the wider one, as the documented formula does
      ok &= expected[0] > expected[1] && got[0] > got[1];
      assertTrue("IntervalQuery saturation (pivot 1) does not score the documented formula:" + msg, ok);
    }
  }
}
