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
package org.apache.lucene.util;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.boost.DelimitedBoostTokenFilter;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: per-token boosts (BoostAttribute, set here by the stock
 * DelimitedBoostTokenFilter, as in weighted synonyms) in quoted phrases.
 *
 * <p>Query analyzer: WhitespaceTokenizer -> SynonymGraphFilter -> DelimitedBoostTokenFilter('|').
 * Index analyzer: whitespace only. Similarity: BM25 everywhere. Scores are read back from one
 * single-segment index, so the oracle is the score ratio of a boosted query to its unboosted twin.
 */
public class TestPhraseTokenBoosts extends LuceneTestCase {

  private static final String F = "body";

  private static Analyzer queryAnalyzer(SynonymMap map) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tok = new WhitespaceTokenizer();
        TokenStream ts = new SynonymGraphFilter(tok, map, true);
        ts = new DelimitedBoostTokenFilter(ts, '|');
        return new TokenStreamComponents(tok, ts);
      }
    };
  }

  private static SynonymMap synonyms(String[][] rules) throws IOException {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    CharsRefBuilder r1 = new CharsRefBuilder();
    CharsRefBuilder r2 = new CharsRefBuilder();
    for (String[] rule : rules) {
      // rule[0] -> rule[1], keeping the original
      b.add(
          SynonymMap.Builder.join(rule[0].split(" "), r1),
          SynonymMap.Builder.join(rule[1].split(" "), r2),
          true);
    }
    return b.build();
  }

  /** Index the given docs (one segment) and return the score of each doc for q (0 = no match). */
  private static float[] scores(Query q, String... docs) throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer());
      iwc.setSimilarity(new BM25Similarity());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (String d : docs) {
          Document doc = new Document();
          doc.add(new TextField(F, d, Field.Store.NO));
          w.addDocument(doc);
        }
        w.forceMerge(1);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher s = new IndexSearcher(r);
        s.setSimilarity(new BM25Similarity());
        float[] out = new float[docs.length];
        for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
          out[sd.doc] = sd.score;
        }
        return out;
      }
    }
  }

  private static final String[] DOCS = {
    "fast truck", "fast car", "fast guinea pig", "cat food", "kitty food", "slow automobile"
  };

  private static float ratio(QueryBuilder qb, String boosted, String plain, int doc)
      throws IOException {
    Query qb1 = qb.createPhraseQuery(F, boosted);
    Query qb0 = qb.createPhraseQuery(F, plain);
    float sb = scores(qb1, DOCS)[doc];
    float s0 = scores(qb0, DOCS)[doc];
    System.out.println(
        "boosted=" + qb1 + "  score=" + sb + " | plain=" + qb0 + "  score=" + s0);
    assertTrue("both must match doc " + doc, sb > 0 && s0 > 0);
    return sb / s0;
  }

  private static SynonymMap commonSynonyms() throws IOException {
    return synonyms(
        new String[][] {
          {"car", "automobile"}, // single-word synonym (stacked, posLen 1)
          {"guinea pig", "cavy"}, // multi-word synonym (token graph)
          {"cat", "kitty|0.5"} // weighted single-word synonym
        });
  }

  /** Control 1: plain phrase (no stacked token): phrase boost = product of token boosts. */
  public void testControlPlainPhraseKeepsTokenBoost() throws IOException {
    QueryBuilder qb = new QueryBuilder(queryAnalyzer(commonSynonyms()));
    assertEquals(0.5f, ratio(qb, "fast|0.5 truck", "fast truck", 0), 1e-6f);
  }

  /** Control 2: another word has a MULTI-word synonym (graph phrase): the boost is kept. */
  public void testControlGraphPhraseKeepsTokenBoost() throws IOException {
    QueryBuilder qb = new QueryBuilder(queryAnalyzer(commonSynonyms()));
    assertEquals(0.5f, ratio(qb, "fast|0.5 guinea pig", "fast guinea pig", 2), 1e-6f);
  }

  /** Claim 1: another word has a SINGLE-word synonym (MultiPhraseQuery path): boost on 'fast'. */
  public void testBoostOnUnsynonymedWordWhenOtherWordHasSingleWordSynonym() throws IOException {
    QueryBuilder qb = new QueryBuilder(queryAnalyzer(commonSynonyms()));
    float rt = ratio(qb, "fast|0.5 car", "fast car", 1);
    assertEquals(
        "token boost 0.5 on 'fast' should halve the phrase score as it does for \"fast|0.5 truck\""
            + " and \"fast|0.5 guinea pig\"; ratio",
        0.5f,
        rt,
        1e-6f);
  }

  /** Same as claim 1, reached through the classic QueryParser with a quoted phrase. */
  public void testBoostOnUnsynonymedWordClassicQueryParser() throws Exception {
    QueryParser qp = new QueryParser(F, queryAnalyzer(commonSynonyms()));
    Query qBoost = qp.parse("\"fast|0.5 car\"");
    Query qPlain = qp.parse("\"fast car\"");
    Query cBoost = qp.parse("\"fast|0.5 truck\"");
    Query cPlain = qp.parse("\"fast truck\"");
    float control = scores(cBoost, DOCS)[0] / scores(cPlain, DOCS)[0];
    float rt = scores(qBoost, DOCS)[1] / scores(qPlain, DOCS)[1];
    System.out.println("parser: " + cBoost + " ratio=" + control + " | " + qBoost + " ratio=" + rt);
    assertEquals(0.5f, control, 1e-6f);
    assertEquals("QueryParser \"fast|0.5 car\" ratio", 0.5f, rt, 1e-6f);
  }

  /**
   * Claim 2: weighted single-word synonym 'cat -> kitty|0.5'. The unquoted query ranks 'kitty
   * food' below 'cat food'; the quoted phrase is expected to as well.
   */
  public void testWeightedSingleWordSynonymInPhrase() throws IOException {
    QueryBuilder qb = new QueryBuilder(queryAnalyzer(commonSynonyms()));
    Query bool = qb.createBooleanQuery(F, "cat food");
    float[] sb = scores(bool, DOCS);
    System.out.println("unquoted=" + bool + " cat food=" + sb[3] + " kitty food=" + sb[4]);
    assertTrue("control: unquoted query must rank 'kitty food' below 'cat food'", sb[4] < sb[3]);
    Query phrase = qb.createPhraseQuery(F, "cat food");
    float[] sp = scores(phrase, DOCS);
    System.out.println("quoted=" + phrase + " cat food=" + sp[3] + " kitty food=" + sp[4]);
    assertTrue("both docs must match the phrase", sp[3] > 0 && sp[4] > 0);
    assertTrue(
        "weighted synonym kitty|0.5 should rank 'kitty food' below 'cat food' in "
            + phrase
            + " too; scores "
            + sp[3]
            + " vs "
            + sp[4],
        sp[4] < sp[3]);
  }
}
