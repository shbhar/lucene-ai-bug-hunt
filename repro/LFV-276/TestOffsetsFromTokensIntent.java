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
package org.apache.lucene.search.matchhighlight;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.MatchesUtils;
import org.apache.lucene.search.MultiTermQuery;
import org.apache.lucene.search.PrefixQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Intent probes for OffsetsFromTokens on an untokenized, multivalued StringField (indexed DOCS
 * only), which is the "keyword-like" case the upstream test names. Each failing test asserts that
 * the default strategy's output depends only on which terms matched, not on the query class or
 * its rewrite method.
 */
public class TestOffsetsFromTokensIntent extends LuceneTestCase {

  private static final String F = "tag";

  private Analyzer analyzer() {
    return new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
  }

  private Directory index(Analyzer analyzer) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new StringField(F, "lucene", Field.Store.YES));
      doc.add(new StringField(F, "solr", Field.Store.YES));
      w.addDocument(doc);
      w.commit();
    }
    return dir;
  }

  private Map<String, List<OffsetRange>> hits(Query query) throws IOException {
    Analyzer analyzer = analyzer();
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query rewritten = searcher.rewrite(query);
      TopDocs topDocs = searcher.search(rewritten, 10);
      assertEquals("the query matches the document", 1, topDocs.scoreDocs.length);
      List<Map<String, List<OffsetRange>>> all = new ArrayList<>();
      new MatchRegionRetriever(searcher, rewritten, analyzer, _ -> false, _ -> true)
          .highlightDocuments(topDocs, (_, _, _, _, fh) -> all.add(new TreeMap<>(fh)));
      assertEquals(1, all.size());
      return all.get(0);
    }
  }

  private static final Map<String, List<OffsetRange>> LUCENE_HIT =
      Map.of(F, List.of(new OffsetRange(0, 6)));

  /** Control: a TermQuery on the keyword field highlights the matching value. */
  public void testTermQueryControl() throws IOException {
    assertEquals(LUCENE_HIT, hits(new TermQuery(new Term(F, "lucene"))));
  }

  /** Control: a one-term TermInSetQuery reports through consumeTerms and highlights. */
  public void testTermInSetOneTermControl() throws IOException {
    assertEquals(LUCENE_HIT, hits(new TermInSetQuery(F, List.of(new BytesRef("lucene")))));
  }

  /**
   * A two-term TermInSetQuery (exact terms, no wildcard) should give the same regions as the
   * equivalent disjunction of TermQuery clauses.
   */
  public void testTermInSetTwoTermsEqualsTermDisjunction() throws IOException {
    Query disjunction =
        new BooleanQuery.Builder()
            .add(new TermQuery(new Term(F, "lucene")), BooleanClause.Occur.SHOULD)
            .add(new TermQuery(new Term(F, "elastic")), BooleanClause.Occur.SHOULD)
            .build();
    Map<String, List<OffsetRange>> viaTermQueries = hits(disjunction);
    assertEquals(LUCENE_HIT, viaTermQueries);
    assertEquals(
        viaTermQueries,
        hits(new TermInSetQuery(F, List.of(new BytesRef("lucene"), new BytesRef("elastic")))));
  }

  /** The same PrefixQuery should give the same regions whatever its rewrite method. */
  public void testPrefixQueryRewriteMethodIndependence() throws IOException {
    Map<String, List<OffsetRange>> scoring =
        hits(new PrefixQuery(new Term(F, "luc"), MultiTermQuery.SCORING_BOOLEAN_REWRITE));
    assertEquals(LUCENE_HIT, scoring);
    assertEquals(scoring, hits(new PrefixQuery(new Term(F, "luc"))));
  }

  /**
   * Fact check (passes): for the default PrefixQuery the Matches API reports a real match on the
   * field (not null and not MATCH_WITH_NO_TERMS), whose getQuery() is the multi-term query itself.
   */
  public void testMatchesApiReportsFieldMatch() throws IOException {
    Analyzer analyzer = analyzer();
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query rewritten = searcher.rewrite(new PrefixQuery(new Term(F, "luc")));
      Weight weight = searcher.createWeight(rewritten, ScoreMode.COMPLETE, 1f);
      Matches matches = weight.matches(reader.leaves().get(0), 0);
      assertNotNull(matches);
      MatchesIterator mi = matches.getMatches(F);
      assertNotNull(mi);
      assertNotSame(MatchesUtils.MATCH_WITH_NO_TERMS, mi);
      assertTrue(mi.next());
      assertEquals(-1, mi.startPosition());
      assertTrue(
          "getQuery() is " + mi.getQuery().getClass().getName(),
          mi.getQuery() instanceof PrefixQuery);
    }
  }
}
