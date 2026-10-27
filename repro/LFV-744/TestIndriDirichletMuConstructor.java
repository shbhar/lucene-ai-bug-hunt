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
package org.apache.lucene.search.similarities;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Scorer;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.Weight;
import org.apache.lucene.search.similarities.IndriDirichletSimilarity.IndriCollectionModel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndriDirichletSimilarity(float mu) does not pass a collection model, so it gets LMSimilarity's
 * DefaultCollectionModel, (cf + 1) / (|C| + 1), while IndriDirichletSimilarity() uses
 * IndriCollectionModel, cf / |C|. The same mu therefore gives different scores.
 */
public class TestIndriDirichletMuConstructor extends LuceneTestCase {

  // 25 tokens in field "body"; "president" occurs 3 times, "george" once.
  private static final String[] DOCS = {
    "president washington was the first leader",
    "the president is head of the executive branch",
    "george washington was a general",
    "a company can have a president"
  };
  private static final int TOTAL_TOKENS = 25;

  private Directory dir;
  private DirectoryReader reader;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (String text : DOCS) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /**
   * Score of one document, read through Weight#scorer. (On 10.3+ IndexSearcher#search(TermQuery, n)
   * returns no hits under this similarity because its scores are negative, a separate issue.)
   */
  private float score(Similarity sim, String term, int doc) throws IOException {
    IndexSearcher searcher = new IndexSearcher(reader);
    searcher.setSimilarity(sim);
    Query q = new TermQuery(new Term("body", term));
    Weight weight = searcher.createWeight(searcher.rewrite(q), ScoreMode.COMPLETE, 1f);
    LeafReaderContext leaf = reader.leaves().get(0);
    Scorer scorer = weight.scorer(leaf);
    assertNotNull(scorer);
    assertEquals(doc, scorer.iterator().advance(doc));
    return scorer.score();
  }

  /** Indri's Dirichlet estimate, log((tf + mu * cf / |C|) / (|D| + mu)). */
  private static float indri(int tf, int cf, int docLen, float mu) {
    return (float) Math.log((tf + mu * ((double) cf / TOTAL_TOKENS)) / (docLen + mu));
  }

  /** The no-argument constructor is documented as "the default mu value of 2000". */
  public void testMu2000ScoresLikeNoArgConstructor() throws IOException {
    Similarity byDefault = new IndriDirichletSimilarity();
    Similarity explicitMu = new IndriDirichletSimilarity(2000f);
    assertEquals("LM IndriDirichlet(2000.000000)", byDefault.toString());
    assertEquals(byDefault.toString(), explicitMu.toString());
    // doc 0 "president washington was the first leader": tf 1, |D| 6; cf("president") 3
    assertEquals(indri(1, 3, 6, 2000f), score(byDefault, "president", 0), 1e-6f); // -2.119101
    assertEquals(
        "IndriDirichletSimilarity(2000f) vs IndriDirichletSimilarity()",
        score(byDefault, "president", 0),
        score(explicitMu, "president", 0), // -1.871553
        1e-6f);
  }

  /**
   * Any mu: the class implements Dirichlet smoothing "as implemented in the Indri Search engine".
   */
  public void testMuConstructorFollowsIndriFormula() throws IOException {
    float mu = 3f;
    // doc 2 "george washington was a general": tf 1, |D| 5; cf("george") 1
    assertEquals(
        "IndriDirichletSimilarity(3f), george, doc 2",
        indri(1, 1, 5, mu), // -1.9661129
        score(new IndriDirichletSimilarity(mu), "george", 2), // -1.8718022
        1e-6f);
  }

  /** Control (passes): with IndriCollectionModel passed explicitly, the same mu follows Indri. */
  public void testExplicitIndriCollectionModelFollowsIndriFormula() throws IOException {
    float mu = 3f;
    Similarity sim = new IndriDirichletSimilarity(new IndriCollectionModel(), mu);
    assertEquals(indri(1, 1, 5, mu), score(sim, "george", 2), 1e-6f);
    assertEquals(
        indri(1, 3, 6, 2000f),
        score(new IndriDirichletSimilarity(new IndriCollectionModel(), 2000f), "president", 0),
        1e-6f);
  }
}
