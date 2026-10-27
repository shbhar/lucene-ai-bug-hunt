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
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * With three or more boosted terms, SynonymScorer#freq sums boost * freq in float in the order of
 * the disjunction's topList, which depends on how the iterator reached the document. The same
 * document then gets different scores (1 ulp apart) from nextDoc() and advance(), so explain() does
 * not match the score of a search.
 */
public class TestSynonymFreqSumOrder extends LuceneTestCase {

  private static final Similarity SIM = new BM25Similarity();
  private static final String[] DOCS = {
    "b", "", "a a a a b b c c c", "a a a a b c c c", "c c c c", "a a b b"
  };

  private static Query query() {
    return new SynonymQuery.Builder("f")
        .addTerm(new BytesRef("a"), 0.5f)
        .addTerm(new BytesRef("b"), 0.2f)
        .addTerm(new BytesRef("c"), 0.70000005f)
        .build();
  }

  private static Directory index() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setSimilarity(SIM);
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (String text : DOCS) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  private static IndexSearcher searcher(DirectoryReader reader) {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(SIM);
    searcher.setQueryCache(null);
    return searcher;
  }

  /** The explanation's value must equal the score of the hit (CheckHits.verifyExplanation). */
  public void testExplainMatchesScore() throws Exception {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = searcher(reader);
      Query q = query();
      for (ScoreDoc hit : searcher.search(q, 10).scoreDocs) {
        float explained = searcher.explain(q, hit.doc).getValue().floatValue();
        assertEquals("doc " + hit.doc + ": score " + hit.score + " explain " + explained,
            hit.score, explained, 0f);
      }
    }
  }

  /** A document's score must not depend on whether the scorer reached it by nextDoc or advance. */
  public void testScoreIndependentOfIterationPath() throws Exception {
    try (Directory dir = index();
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = searcher(reader);
      Weight weight = searcher.createWeight(searcher.rewrite(query()), ScoreMode.COMPLETE, 1f);
      LeafReaderContext ctx = reader.leaves().get(0);
      for (int target = 0; target < DOCS.length; target++) {
        Scorer byNextDoc = weight.scorer(ctx);
        int doc = byNextDoc.iterator().nextDoc();
        while (doc < target) {
          doc = byNextDoc.iterator().nextDoc();
        }
        if (doc != target) {
          continue; // no match
        }
        Scorer byAdvance = weight.scorer(ctx);
        assertEquals(target, byAdvance.iterator().advance(target));
        assertEquals(
            "doc " + target + " scored by nextDoc vs advance",
            byNextDoc.score(), byAdvance.score(), 0f);
      }
    }
  }
}
