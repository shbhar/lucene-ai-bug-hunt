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
package org.apache.lucene.misc;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.search.CheckHits;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * SweetSpotSimilarity with a length plateau (setLengthNormFactors(10, 20, 0.5f)): IndexSearcher's
 * default top-k search (TOP_SCORES once 1,000 hits are counted) must return the same top hits as a
 * complete evaluation.
 */
public class TestSweetSpotTopScores extends LuceneTestCase {

  public void testTopHitsMatchCompleteEvaluation() throws Exception {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(10, 20, 0.5f); // lengths 10..20 are equally good, shorter ones worse
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setCodec(TestUtil.getDefaultCodec())
                  .setSimilarity(sim)
                  .setMergePolicy(NoMergePolicy.INSTANCE)
                  .setMaxBufferedDocs(100_000))) {
        // docs 0..1999: "t" once in a 10-term field (in the plateau): score tf(1) * norm(10)
        for (int i = 0; i < 2000; i++) {
          Document doc = new Document();
          doc.add(new StoredField("id", i));
          doc.add(new TextField("f", "t a a a a a a a a a", Field.Store.NO));
          w.addDocument(doc);
        }
        // docs 2000..2999: "t" twice in a 2-term field (below the plateau), except doc 2150, which
        // has "t" twice in a 10-term field and is therefore the best match of the whole index
        for (int i = 0; i < 1000; i++) {
          Document doc = new Document();
          doc.add(new StoredField("id", 2000 + i));
          doc.add(new TextField("f", i == 150 ? "t t a a a a a a a a" : "t t", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false);
        searcher.setSimilarity(sim);
        Query query = new TermQuery(new Term("f", "t"));
        TopDocs complete =
            searcher.search(query, new TopScoreDocCollectorManager(10, null, Integer.MAX_VALUE));
        assertEquals("the complete evaluation ranks doc 2150 first", 2150, complete.scoreDocs[0].doc);
        TopDocs top = searcher.search(query, 10); // default: TOP_SCORES after 1,000 hits
        assertEquals(
            "best hit of IndexSearcher.search(query, 10); complete evaluation: doc 2150 score "
                + complete.scoreDocs[0].score
                + ", got doc "
                + top.scoreDocs[0].doc
                + " score "
                + top.scoreDocs[0].score,
            complete.scoreDocs[0].doc,
            top.scoreDocs[0].doc);
      }
    }
  }

  /** Lucene's own pruning consistency checker on the same index. */
  public void testCheckHitsTopScores() throws Exception {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setLengthNormFactors(10, 20, 0.5f);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(
              dir,
              new IndexWriterConfig(new MockAnalyzer(random()))
                  .setCodec(TestUtil.getDefaultCodec())
                  .setSimilarity(sim)
                  .setMergePolicy(NoMergePolicy.INSTANCE)
                  .setMaxBufferedDocs(100_000))) {
        for (int i = 0; i < 3000; i++) {
          Document doc = new Document();
          doc.add(new TextField("f", i % 3 == 0 ? "t a a a a a a a a a" : "t t", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader, false);
        searcher.setSimilarity(sim);
        CheckHits.checkTopScores(random(), new TermQuery(new Term("f", "t")), searcher);
      }
    }
  }
}
