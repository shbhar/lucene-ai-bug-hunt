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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When every calibrated score is below 1e-7 (e.g. a small base rate such as the 1e-6 that
 * BayesianScoreEstimator can return), BayesianScoreScorer#setMinCompetitiveScore clamps the
 * minimum competitive score up to 1e-7 before inverting the sigmoid. The inner scorer is then
 * told to skip documents that are still competitive, and a top-k search returns the wrong hits.
 */
public class TestBayesianScoreTopScores extends LuceneTestCase {

  public void testTopHitIsNotPruned() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));
    for (int i = 0; i < 3900; i++) { // docs 0..3899 do not match
      Document doc = new Document();
      doc.add(new TextField("body", "filler filler", Field.Store.NO));
      w.addDocument(doc);
    }
    for (int i = 0; i < 1100; i++) { // docs 3900..4999: weak matches (one occurrence, longer field)
      Document doc = new Document();
      doc.add(new TextField("body", "a filler filler filler", Field.Store.NO));
      w.addDocument(doc);
    }
    Document best = new Document(); // doc 5000: the best match
    best.add(new TextField("body", "a a a", Field.Store.NO));
    w.addDocument(best);
    w.forceMerge(1);
    DirectoryReader reader = DirectoryReader.open(w);
    w.close();
    IndexSearcher searcher = new IndexSearcher(reader);

    Query query = new BayesianScoreQuery(new TermQuery(new Term("body", "a")), 1f, 5f, 1e-6f);

    // Exhaustive reference (no pruning).
    TopDocs complete = searcher.search(query, new TopScoreDocCollectorManager(1, Integer.MAX_VALUE));
    assertEquals(5000, complete.scoreDocs[0].doc);
    assertTrue("calibrated scores are tiny", complete.scoreDocs[0].score < 1e-7f);

    // Default top-k search: dynamic pruning starts after 1000 hits.
    TopDocs top = searcher.search(query, 1);
    float explained = searcher.explain(query, 5000).getValue().floatValue();
    reader.close();
    dir.close();
    assertEquals(
        "the best hit (doc 5000, score "
            + complete.scoreDocs[0].score
            + ", explain "
            + explained
            + ") must be returned, got doc "
            + top.scoreDocs[0].doc
            + " with score "
            + top.scoreDocs[0].score,
        5000,
        top.scoreDocs[0].doc);
  }
}
