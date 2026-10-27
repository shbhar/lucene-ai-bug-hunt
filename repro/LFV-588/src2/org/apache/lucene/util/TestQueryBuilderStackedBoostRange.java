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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.boost.DelimitedBoostTokenFilter;
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
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;

/**
 * QueryBuilder (LUCENE-9171) turns a BoostAttribute into a query boost. For a token that has a
 * position of its own the boost becomes a BoostQuery and any non-negative value works; for a token
 * stacked with other tokens at one position (a synonym) the boost goes to SynonymQuery.Builder,
 * which accepts only (0, 1], so building the query throws IllegalArgumentException.
 */
public class TestQueryBuilderStackedBoostRange extends LuceneTestCase {

  private static SynonymMap synonyms(String rules) throws Exception {
    SolrSynonymParser parser = new SolrSynonymParser(true, true, new WhitespaceAnalyzer());
    parser.parse(new StringReader(rules));
    return parser.build();
  }

  /** The weighted-synonym chain: whitespace, synonym graph, then delimited boosts. */
  private static Analyzer weightedSynonyms(SynonymMap map) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream stream = new SynonymGraphFilter(tokenizer, map, true);
        stream = new DelimitedBoostTokenFilter(stream, '|');
        return new TokenStreamComponents(tokenizer, stream);
      }
    };
  }

  /** Delimited boosts typed by the user, then synonyms. */
  private static Analyzer userBoostsThenSynonyms(SynonymMap map) {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new WhitespaceTokenizer();
        TokenStream stream = new DelimitedBoostTokenFilter(tokenizer, '|');
        stream = new SynonymGraphFilter(stream, map, true);
        return new TokenStreamComponents(tokenizer, stream);
      }
    };
  }

  private static int count(Analyzer a, Query q, String... docs) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      for (String d : docs) {
        Document doc = new Document();
        doc.add(new TextField("body", d, Field.Store.NO));
        w.addDocument(doc);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        return new IndexSearcher(r).count(q);
      }
    }
  }

  /** Synonym weight 1.5 on an equivalent-synonym rule: every query with 'leopard' fails. */
  public void testSynonymWeightAboveOne() throws Exception {
    Analyzer a = weightedSynonyms(synonyms("leopard, panthera|1.5\nlion => simba|1.5\n"));
    QueryBuilder qb = new QueryBuilder(a);
    // the same weight on a token that has a position of its own is accepted
    Query simba = qb.createBooleanQuery("body", "lion");
    assertTrue(simba.toString(), simba instanceof BoostQuery);
    assertEquals(1.5f, ((BoostQuery) simba).getBoost(), 0f);
    Query q;
    try {
      q = qb.createBooleanQuery("body", "leopard");
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          "QueryBuilder.createBooleanQuery(\"leopard\") threw for synonym weight 1.5 on a stacked"
              + " token, although the same weight on an unstacked token gives "
              + simba,
          e);
    }
    assertEquals(2, count(a, q, "leopard", "panthera", "tiger"));
  }

  /** A boost typed into the query: 'tiger|2' works, 'leopard|2' (a word with a synonym) fails. */
  public void testUserBoostOnWordWithSynonym() throws Exception {
    Analyzer a = userBoostsThenSynonyms(synonyms("leopard, panthera\n"));
    QueryBuilder qb = new QueryBuilder(a);
    Query tiger = qb.createBooleanQuery("body", "tiger|2");
    assertEquals("(body:tiger)^2.0", tiger.toString());
    try {
      qb.createBooleanQuery("body", "leopard|2");
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          "QueryBuilder.createBooleanQuery(\"leopard|2\") threw, although \"tiger|2\" gives " + tiger,
          e);
    }
  }

  /** The classic query parser lets the IllegalArgumentException escape parse(). */
  public void testClassicQueryParser() throws Exception {
    Analyzer a = weightedSynonyms(synonyms("leopard, panthera|1.5\n"));
    QueryParser qp = new QueryParser("body", a);
    try {
      qp.parse("spotted leopard");
    } catch (ParseException e) {
      // a ParseException would at least be the documented failure mode
    } catch (IllegalArgumentException e) {
      throw new AssertionError(
          "QueryParser.parse(\"spotted leopard\") threw IllegalArgumentException (only ParseException"
              + " is declared)",
          e);
    }
  }
}
