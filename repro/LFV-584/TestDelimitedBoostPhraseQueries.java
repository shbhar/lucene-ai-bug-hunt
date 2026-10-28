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
package org.apache.lucene.analysis.boost;

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SolrSynonymParser;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * Token boosts from DelimitedBoostTokenFilter (typed "word|weight" boosts and weighted synonyms
 * such as "cat, kitty|0.5") in phrases built by QueryBuilder. A plain PhraseQuery gets the product
 * of its tokens' boosts. When some word of the phrase has a single-word synonym, QueryBuilder
 * builds a MultiPhraseQuery instead, and every boost of the phrase is ignored.
 */
public class TestDelimitedBoostPhraseQueries extends LuceneTestCase {

  private static SynonymMap synonyms(String rules) throws Exception {
    SolrSynonymParser parser = new SolrSynonymParser(true, true, new WhitespaceAnalyzer());
    parser.parse(new StringReader(rules));
    return parser.build();
  }

  /** Whitespace, then the boost and synonym filters in the given order. */
  private static Analyzer analyzer(SynonymMap map, boolean boostsFirst) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream stream = tokenizer;
        if (boostsFirst) {
          stream = new DelimitedBoostTokenFilter(stream, '|');
        }
        stream = new SynonymGraphFilter(stream, map, true);
        if (boostsFirst == false) {
          stream = new DelimitedBoostTokenFilter(stream, '|');
        }
        return new TokenStreamComponents(tokenizer, stream);
      }
    };
  }

  /** Scores of q on the documents, indexed with a plain whitespace analyzer (no synonyms). */
  private static float[] scores(String[] docs, Query q) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
      for (String d : docs) {
        Document doc = new Document();
        doc.add(new TextField("body", d, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        float[] out = new float[docs.length];
        for (ScoreDoc sd : s.search(q, 10).scoreDocs) {
          out[sd.doc] = sd.score;
        }
        return out;
      }
    }
  }

  /** A typed boost on a word without synonyms is lost once another word has a synonym. */
  public void testTypedBoostKeptWhenAnotherWordHasSynonym() throws Exception {
    QueryBuilder qb = new QueryBuilder(analyzer(synonyms("car, automobile\n"), true));
    // control: without a synonym in the phrase the boost is applied
    assertEquals(
        "(body:\"fast tiger\")^0.5", qb.createPhraseQuery("body", "fast|0.5 tiger").toString());
    String[] docs = {"fast car"};
    float plain = scores(docs, qb.createPhraseQuery("body", "fast car"))[0];
    Query boosted = qb.createPhraseQuery("body", "fast|0.5 car");
    float got = scores(docs, boosted)[0];
    assertTrue(plain > 0);
    assertEquals(
        "the boost 0.5 on 'fast' should halve the score of \"fast car\"; query was " + boosted,
        plain * 0.5f,
        got,
        plain * 1e-5f);
  }

  /** A weighted single-word synonym is honoured unquoted and in a graph phrase, not in quotes. */
  public void testWeightedSynonymInPhrase() throws Exception {
    QueryBuilder qb =
        new QueryBuilder(analyzer(synonyms("cat, kitty|0.5\nbig cat, feline|0.5\n"), false));
    String[] docs = {"cat food", "kitty food"};
    // control: the unquoted query ranks 'kitty food' below 'cat food'
    float[] unquoted = scores(docs, qb.createBooleanQuery("body", "cat food"));
    assertTrue("unquoted query honours the weight", unquoted[1] < unquoted[0]);
    // control: a multi-word weighted synonym keeps its weight in a phrase (one phrase per path)
    assertTrue(qb.createPhraseQuery("body", "big cat food").toString().contains("food\")^0.5"));
    Query phrase = qb.createPhraseQuery("body", "cat food");
    float[] quoted = scores(docs, phrase);
    assertTrue(quoted[0] > 0 && quoted[1] > 0);
    assertTrue(
        "the weighted synonym 'kitty|0.5' should score below 'cat' in the phrase query "
            + phrase
            + " too, but scores are "
            + quoted[0]
            + " and "
            + quoted[1],
        quoted[1] < quoted[0]);
  }
}
