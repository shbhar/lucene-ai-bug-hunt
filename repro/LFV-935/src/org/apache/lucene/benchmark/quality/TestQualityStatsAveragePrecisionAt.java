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
package org.apache.lucene.benchmark.quality;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.benchmark.quality.trec.TrecJudge;
import org.apache.lucene.benchmark.quality.utils.SimpleQQParser;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QualityStats.average(stats).getPrecisionAt(n) must be the mean of the per-query precision at n
 * (the per-query values already count missing ranks as non-relevant, as trec_eval's P_n does).
 * For n above the averaged number of points it instead re-extrapolates an already averaged value.
 */
public class TestQualityStatsAveragePrecisionAt extends LuceneTestCase {

  private static QualityStats stats(boolean[] rel, int maxGoodPoints) {
    QualityStats s = new QualityStats(maxGoodPoints, 0);
    for (int i = 0; i < rel.length; i++) {
      s.addResult(i + 1, rel[i], 0);
    }
    return s;
  }

  /** trec_eval P_n: relevant documents in the top n divided by n (missing ranks = non-relevant). */
  private static double trecEvalP(boolean[] rel, int n) {
    int c = 0;
    for (int i = 0; i < Math.min(n, rel.length); i++) {
      if (rel[i]) {
        c++;
      }
    }
    return (double) c / n;
  }

  /** Two queries: one with 20 results, one with only 2 results. */
  public void testAveragePrecisionAtIsMeanOfPerQueryValues() {
    boolean[] q1 = new boolean[20]; // relevant at ranks 1, 10, 15, 20
    q1[0] = q1[9] = q1[14] = q1[19] = true;
    boolean[] q2 = {true, true}; // two results, both relevant
    QualityStats s1 = stats(q1, 4);
    QualityStats s2 = stats(q2, 2);
    QualityStats avg = QualityStats.average(new QualityStats[] {s1, s2});

    StringBuilder wrong = new StringBuilder();
    for (int n = 1; n <= QualityStats.MAX_POINTS; n++) {
      double meanOfPerQuery = (s1.getPrecisionAt(n) + s2.getPrecisionAt(n)) / 2;
      double trecEval = (trecEvalP(q1, n) + trecEvalP(q2, n)) / 2;
      // the per-query values agree with trec_eval; only the averaged object is off
      assertEquals("per-query P@" + n, trecEval, meanOfPerQuery, 1e-12);
      if (Math.abs(avg.getPrecisionAt(n) - meanOfPerQuery) > 1e-12) {
        wrong.append(
            String.format(
                java.util.Locale.ROOT,
                "%n  P@%d: average()=%.6f, mean of per-query values=%.6f",
                n,
                avg.getPrecisionAt(n),
                meanOfPerQuery));
      }
    }
    assertTrue(
        "average().getPrecisionAt(n) differs from the mean per-query P@n (avg.getNumPoints()="
            + avg.getNumPoints()
            + "):"
            + wrong,
        wrong.length() == 0);
  }

  /**
   * End to end through QualityBenchmark with maxResults = 10: query "apple" returns 10 hits, all
   * relevant (P@10 = 1.0); query "banana" returns its only 2 hits, both relevant (P@10 = 0.2). The
   * mean P@10 is 0.6.
   */
  public void testMeanPrecisionAt10EndToEnd() throws Exception {
    try (Directory dir = newDirectory()) {
      StringBuilder qrels = new StringBuilder();
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (int i = 0; i < 12; i++) {
          Document doc = new Document();
          String name = "d" + i;
          doc.add(new StringField("docname", name, Field.Store.YES));
          doc.add(new TextField("body", i < 10 ? "apple" : "banana", Field.Store.NO));
          w.addDocument(doc);
          qrels.append(i < 10 ? "1" : "2").append(" 0 ").append(name).append(" 1\n");
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        QualityQuery[] qqs = {
          new QualityQuery("1", Map.of("title", "apple")),
          new QualityQuery("2", Map.of("title", "banana"))
        };
        Judge judge = new TrecJudge(new BufferedReader(new StringReader(qrels.toString())));
        QualityBenchmark run =
            new QualityBenchmark(qqs, new SimpleQQParser("title", "body"), searcher, "docname");
        run.setMaxResults(10);
        QualityStats[] stats = run.execute(judge, null, null);
        assertEquals(10.0, stats[0].getNumPoints(), 0);
        assertEquals(2.0, stats[1].getNumPoints(), 0);
        assertEquals(1.0, stats[0].getPrecisionAt(10), 1e-12);
        assertEquals(0.2, stats[1].getPrecisionAt(10), 1e-12);

        QualityStats avg = QualityStats.average(stats);
        assertEquals("mean P@10 over the two queries", 0.6, avg.getPrecisionAt(10), 1e-12);
      }
    }
  }
}
