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
package org.apache.lucene.benchmark.quality.trec;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.benchmark.quality.QualityBenchmark;
import org.apache.lucene.benchmark.quality.QualityQuery;
import org.apache.lucene.benchmark.quality.QualityStats;
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
 * TREC qrels carry an integer relevance grade; trec_eval counts a judged document as relevant only
 * when its grade is at least 1 (negative grades, e.g. -2 for junk pages in some TREC tracks, are
 * non-relevant). TrecJudge treats every grade other than the literal string "0" as relevant.
 */
public class TestTrecJudgeGrades extends LuceneTestCase {

  public void testNonPositiveGradesAreNotRelevant() throws Exception {
    String qrels =
        "7 0 d1 1\n" // relevant
            + "7 0 d2 -2\n" // junk: non-relevant for trec_eval
            + "7 0 d3 0\n" // non-relevant
            + "7 0 d4 -1\n"; // non-relevant for trec_eval
    TrecJudge judge = new TrecJudge(new BufferedReader(new StringReader(qrels)));
    QualityQuery q = new QualityQuery("7", Map.of());
    assertTrue(judge.isRelevant("d1", q));
    assertFalse(judge.isRelevant("d3", q));
    assertEquals("number of relevant documents (grade >= 1)", 1, judge.maxRecall(q));
    assertFalse("grade -2 judged relevant", judge.isRelevant("d2", q));
    assertFalse("grade -1 judged relevant", judge.isRelevant("d4", q));
  }

  /**
   * End to end: the junk page (grade -2) ranks first, the relevant page second. trec_eval gives P@1
   * = 0, AP = 0.5 and one relevant document; the benchmark reports P@1 = 1 and AP = 1.
   */
  public void testNegativeGradeInflatesMetricsEndToEnd() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        Document junk = new Document();
        junk.add(new StringField("docname", "junk", Field.Store.YES));
        junk.add(new TextField("body", "apple apple apple", Field.Store.NO));
        w.addDocument(junk);
        Document good = new Document();
        good.add(new StringField("docname", "good", Field.Store.YES));
        good.add(new TextField("body", "apple pie recipe with cream and sugar", Field.Store.NO));
        w.addDocument(good);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        String qrels = "1 0 junk -2\n1 0 good 1\n";
        TrecJudge judge = new TrecJudge(new BufferedReader(new StringReader(qrels)));
        QualityQuery[] qqs = {new QualityQuery("1", Map.of("title", "apple"))};
        QualityBenchmark run =
            new QualityBenchmark(qqs, new SimpleQQParser("title", "body"), searcher, "docname");
        QualityStats s = run.execute(judge, null, null)[0];
        assertEquals("both documents retrieved", 2.0, s.getNumPoints(), 0);
        // the junk page must rank first for this scenario (higher term frequency, shorter field)
        assertEquals(
            "first hit",
            "junk",
            searcher.storedFields().document(searcher.search(new SimpleQQParser("title", "body")
                    .parse(qqs[0]), 1).scoreDocs[0].doc).get("docname"));
        String got =
            "relevant docs="
                + s.getMaxGoodPoints()
                + " (trec_eval: 1), P@1="
                + s.getPrecisionAt(1)
                + " (trec_eval: 0), AP="
                + s.getAvp()
                + " (trec_eval: 0.5)";
        assertEquals(got, 1.0, s.getMaxGoodPoints(), 0);
        assertEquals(got, 0.0, s.getPrecisionAt(1), 1e-12);
        assertEquals(got, 0.5, s.getAvp(), 1e-12);
      }
    }
  }
}
