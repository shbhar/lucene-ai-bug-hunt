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
package org.apache.lucene.queryparser.surround.query;

import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.surround.parser.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A weight (^N) written on the operand of a surround field prefix ("title: lucene^3") must be
 * applied, exactly as it is when the same term is written without the prefix ("lucene^3" with
 * default field "title") or when the weight is put outside the field query ("(title: lucene)^3").
 * Only public API is used: QueryParser.parse, SrndQuery.makeLuceneQueryField, BasicQueryFactory.
 */
public class TestSrndFieldPrefixWeight extends LuceneTestCase {

  private static final String DEFAULT_FIELD = "title";

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setSimilarity(new BM25Similarity());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      // doc 0: "lucene" in a long title
      add(w, "lucene alpha beta gamma delta epsilon zeta eta theta iota", "kappa lambda");
      // doc 1: "lucene" in a one-word body
      add(w, "kappa lambda", "lucene");
      // doc 2: both terms in title and body
      add(w, "lucene search", "lucene search mu");
      // doc 3: filler so that idf is not degenerate
      add(w, "nu xi omicron", "pi rho sigma");
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
    searcher.setSimilarity(new BM25Similarity());
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static void add(IndexWriter w, String title, String body) throws Exception {
    Document d = new Document();
    d.add(new TextField("title", title, Field.Store.NO));
    d.add(new TextField("body", body, Field.Store.NO));
    w.addDocument(d);
  }

  private Map<Integer, Float> scores(String surround) throws Exception {
    Query q =
        QueryParser.parse(surround).makeLuceneQueryField(DEFAULT_FIELD, new BasicQueryFactory());
    TopDocs td = searcher.search(q, 100);
    Map<Integer, Float> m = new HashMap<>();
    for (ScoreDoc sd : td.scoreDocs) {
      m.put(sd.doc, sd.score);
    }
    return m;
  }

  private void assertSameScores(String expectedQuery, String actualQuery) throws Exception {
    Map<Integer, Float> exp = scores(expectedQuery);
    Map<Integer, Float> act = scores(actualQuery);
    assertFalse("vacuous: no hits for " + expectedQuery, exp.isEmpty());
    assertEquals(
        "hit sets differ: " + expectedQuery + " vs " + actualQuery, exp.keySet(), act.keySet());
    for (Integer doc : exp.keySet()) {
      assertEquals(
          "doc "
              + doc
              + ": score of ["
              + actualQuery
              + "] must equal score of ["
              + expectedQuery
              + "]"
              + " (parsed as "
              + QueryParser.parse(actualQuery)
              + ")",
          exp.get(doc),
          act.get(doc),
          0f);
    }
  }

  private void assertScaled(String unweighted, String weighted, float factor) throws Exception {
    Map<Integer, Float> base = scores(unweighted);
    Map<Integer, Float> w = scores(weighted);
    assertFalse("vacuous: no hits for " + unweighted, base.isEmpty());
    assertEquals(base.keySet(), w.keySet());
    for (Integer doc : base.keySet()) {
      float expected = factor * base.get(doc);
      assertEquals(
          "doc " + doc + ": [" + weighted + "] must score " + factor + " x [" + unweighted + "]",
          expected,
          w.get(doc),
          expected * 1e-5f);
    }
  }

  /** Controls: these weight placements are applied today. They must pass. */
  public void testControlsWeightApplied() throws Exception {
    assertScaled("lucene", "lucene^3", 3f); // default field, no prefix
    assertScaled("title: lucene", "(title: lucene)^3", 3f); // weight outside the field query
    // a weight on a term nested inside the field query's own OR is applied
    assertSameScores("lucene^3 or search", "title: lucene^3 or search");
  }

  /** Single field prefix on a term: title: lucene^3. */
  public void testFieldPrefixTermWeight() throws Exception {
    // a prefix naming the default field must not change the query
    assertSameScores("lucene^3", "title: lucene^3");
    // the weight must scale the score
    assertScaled("title: lucene", "title: lucene^3", 3f);
  }

  /** Ranking consequence inside a disjunction. */
  public void testFieldPrefixTermWeightRanking() throws Exception {
    String control = "or((title: lucene)^5, body: lucene)";
    String prefixed = "or(title: lucene^5, body: lucene)";
    Map<Integer, Float> c = scores(control);
    Map<Integer, Float> p = scores(prefixed);
    // control: the long-title doc 0, boosted 5x, outranks the one-word-body doc 1
    assertTrue("control precondition: " + c, c.get(0) > c.get(1));
    assertTrue(
        "[" + prefixed + "] must rank doc 0 above doc 1 like [" + control + "]: scores " + p,
        p.get(0) > p.get(1));
    assertSameScores(control, prefixed);
  }

  /** Multiple field prefixes on a term: title: body: lucene^3. */
  public void testMultiFieldPrefixTermWeight() throws Exception {
    assertSameScores("(title: body: lucene)^3", "title: body: lucene^3");
  }

  /** Weight on a composite (prefix OR) operand of a field prefix: body: or(lucene, search)^2. */
  public void testFieldPrefixComposedOperandWeight() throws Exception {
    assertSameScores("(body: or(lucene, search))^2", "body: or(lucene, search)^2");
  }

  /** Weight on a distance operand of a field prefix: body: 2w(lucene, search)^2. */
  public void testFieldPrefixDistanceOperandWeight() throws Exception {
    assertSameScores("(body: 2w(lucene, search))^2", "body: 2w(lucene, search)^2");
  }
}
