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
package org.apache.lucene.benchmark.quality.utils;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.benchmark.quality.QualityBenchmark;
import org.apache.lucene.benchmark.quality.QualityQuery;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TotalHits;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SubmissionReport writes TREC run lines (qid Q0 docno rank score tag) "ready for submission", and
 * QueryDriver documents its output as the "submission file for trec_eval". The score column must
 * therefore be a plain number. Scores of 1000 or more are written with a grouping separator
 * ("1,200.5000"), which trec_eval reads with atof() as 1.0.
 */
public class TestSubmissionReportScoreFormat extends LuceneTestCase {

  /** What a C atof()/strtod() reader such as trec_eval takes from the score column. */
  private static double cAtof(String s) {
    int i = 0;
    while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.')) {
      i++;
    }
    return i == 0 ? 0 : Double.parseDouble(s.substring(0, i));
  }

  private static Directory twoDocIndex() throws Exception {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
      for (String name : new String[] {"docA", "docB"}) {
        Document doc = new Document();
        doc.add(new StringField("docname", name, Field.Store.YES));
        doc.add(new TextField("body", name.equals("docA") ? "apple apple" : "apple pie", Field.Store.NO));
        w.addDocument(doc);
      }
    }
    return dir;
  }

  public void testScoreColumnIsANumberTrecEvalCanRead() throws Exception {
    try (Directory dir = twoDocIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      // ranked list: doc 0 (score 1200.5) before doc 1 (score 900.25)
      TopDocs td =
          new TopDocs(
              new TotalHits(2, TotalHits.Relation.EQUAL_TO),
              new ScoreDoc[] {new ScoreDoc(0, 1200.5f), new ScoreDoc(1, 900.25f)});
      StringWriter out = new StringWriter();
      SubmissionReport report = new SubmissionReport(new PrintWriter(out), "run");
      report.report(new QualityQuery("1", Map.of()), td, "docname", searcher);
      report.flush();
      String[] lines = out.toString().trim().split("\\R");
      assertEquals(2, lines.length);
      String score0 = lines[0].trim().split("\\s+")[4];
      String score1 = lines[1].trim().split("\\s+")[4];
      String msg = "score columns [" + score0 + "] [" + score1 + "]";
      assertEquals(msg, 1200.5, cAtof(score0), 1e-4);
      assertEquals(msg, 900.25, cAtof(score1), 1e-4);
      assertTrue("trec_eval must rank the first hit first: " + msg, cAtof(score0) > cAtof(score1));
    }
  }

  /** End to end: QualityBenchmark writing a submission for a boosted query. */
  public void testBoostedQueryEndToEnd() throws Exception {
    try (Directory dir = twoDocIndex();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      QualityQuery[] qqs = {new QualityQuery("1", Map.of("title", "apple"))};
      StringWriter out = new StringWriter();
      SubmissionReport report = new SubmissionReport(new PrintWriter(out), "run");
      QualityBenchmark run =
          new QualityBenchmark(
              qqs,
              qq -> new BoostQuery(new TermQuery(new Term("body", qq.getValue("title"))), 10000f),
              searcher,
              "docname");
      run.execute(null, report, null);
      TopDocs td =
          searcher.search(new BoostQuery(new TermQuery(new Term("body", "apple")), 10000f), 10);
      String[] lines = out.toString().trim().split("\\R");
      assertEquals(td.scoreDocs.length, lines.length);
      for (int i = 0; i < lines.length; i++) {
        String score = lines[i].trim().split("\\s+")[4];
        assertTrue(
            "hit score " + td.scoreDocs[i].score + " written as [" + score + "]",
            Math.abs(cAtof(score) - td.scoreDocs[i].score) < 1e-3);
      }
    }
  }
}
