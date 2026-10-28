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
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
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
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.WildcardQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * One document with a stored, multivalued {@link StringField} {@code tag} = "lucene", "solr"
 * (indexed DOCS only, so MatchRegionRetriever's default strategy is OffsetsFromTokens) and the same
 * values in a positional twin field {@code tag_pos}. Every query below matches the document and the
 * value "lucene", whose hit region is [0, 6). Each test first checks configurations that highlight
 * correctly, then a multi-term query with its default rewrite on {@code tag}, which gets no hit
 * region.
 */
public class TestMatchRegionRetrieverMultiTermNoPositions extends LuceneTestCase {
  private static final String TAG = "tag";
  private static final String TAG_POS = "tag_pos";

  private static Map<String, List<OffsetRange>> luceneHit(String field) {
    return Map.of(field, List.of(new OffsetRange(0, 6)));
  }

  private static Directory index(Analyzer analyzer) throws IOException {
    Directory dir = newDirectory();
    try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      for (String value : List.of("lucene", "solr")) {
        doc.add(new StringField(TAG, value, Field.Store.YES));
        doc.add(new TextField(TAG_POS, value, Field.Store.YES));
      }
      writer.addDocument(doc);
    }
    return dir;
  }

  /** Hit regions per field from MatchRegionRetriever with its default offset strategies. */
  private static Map<String, List<OffsetRange>> hitRegions(String field, Query query)
      throws IOException {
    Analyzer analyzer = new KeywordAnalyzer();
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query rewritten = searcher.rewrite(query);
      TopDocs topDocs = searcher.search(rewritten, 10);
      assertEquals("the query matches the document: " + query, 1, topDocs.totalHits.value());

      // The Matches API reports a real match on the queried field.
      Matches matches =
          searcher
              .createWeight(rewritten, ScoreMode.COMPLETE_NO_SCORES, 1f)
              .matches(reader.leaves().get(0), topDocs.scoreDocs[0].doc);
      assertNotNull(matches);
      MatchesIterator it = matches.getMatches(field);
      assertNotNull(it);
      assertNotSame(MatchesUtils.MATCH_WITH_NO_TERMS, it);
      assertTrue(it.next());

      Map<String, List<OffsetRange>> regions = new TreeMap<>();
      new MatchRegionRetriever(searcher, rewritten, analyzer, _ -> false, _ -> true)
          .highlightDocuments(topDocs, (_, _, _, _, fieldHits) -> regions.putAll(fieldHits));
      return regions;
    }
  }

  public void testPrefixQuery() throws IOException {
    assertEquals(luceneHit(TAG), hitRegions(TAG, new TermQuery(new Term(TAG, "lucene"))));
    assertEquals(
        luceneHit(TAG_POS), hitRegions(TAG_POS, new PrefixQuery(new Term(TAG_POS, "luc"))));
    assertEquals(
        luceneHit(TAG),
        hitRegions(
            TAG, new PrefixQuery(new Term(TAG, "luc"), MultiTermQuery.SCORING_BOOLEAN_REWRITE)));
    // the same PrefixQuery with its default (constant-score) rewrite
    assertEquals(luceneHit(TAG), hitRegions(TAG, new PrefixQuery(new Term(TAG, "luc"))));
  }

  public void testTermInSetQuery() throws IOException {
    assertEquals(
        luceneHit(TAG), hitRegions(TAG, new TermInSetQuery(TAG, List.of(new BytesRef("lucene")))));
    assertEquals(
        luceneHit(TAG),
        hitRegions(
            TAG,
            new BooleanQuery.Builder()
                .add(new TermQuery(new Term(TAG, "lucene")), BooleanClause.Occur.SHOULD)
                .add(new TermQuery(new Term(TAG, "elastic")), BooleanClause.Occur.SHOULD)
                .build()));
    // the same two terms as a TermInSetQuery
    assertEquals(
        luceneHit(TAG),
        hitRegions(
            TAG,
            new TermInSetQuery(TAG, List.of(new BytesRef("lucene"), new BytesRef("elastic")))));
  }

  public void testWildcardQuery() throws IOException {
    assertEquals(
        luceneHit(TAG_POS), hitRegions(TAG_POS, new WildcardQuery(new Term(TAG_POS, "l*e"))));
    assertEquals(luceneHit(TAG), hitRegions(TAG, new WildcardQuery(new Term(TAG, "l*e"))));
  }

  public void testRegexpQuery() throws IOException {
    assertEquals(
        luceneHit(TAG_POS), hitRegions(TAG_POS, new RegexpQuery(new Term(TAG_POS, "luc.*"))));
    assertEquals(luceneHit(TAG), hitRegions(TAG, new RegexpQuery(new Term(TAG, "luc.*"))));
  }

  public void testTermRangeQuery() throws IOException {
    assertEquals(
        luceneHit(TAG_POS),
        hitRegions(TAG_POS, TermRangeQuery.newStringRange(TAG_POS, "l", "m", true, false)));
    assertEquals(
        luceneHit(TAG), hitRegions(TAG, TermRangeQuery.newStringRange(TAG, "l", "m", true, false)));
  }

  /** End to end: MatchHighlighter drops the field instead of highlighting "lucene". */
  public void testMatchHighlighter() throws IOException {
    Analyzer analyzer = new KeywordAnalyzer();
    try (Directory dir = index(analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      MatchHighlighter highlighter =
          new MatchHighlighter(searcher, analyzer)
              .appendFieldHighlighter(
                  FieldValueHighlighters.highlighted(
                      80, 1, new PassageFormatter("...", ">", "<"), TAG::equals))
              .appendFieldHighlighter(FieldValueHighlighters.skipRemaining());
      assertEquals(
          "[>lucene<]", snippet(searcher, highlighter, new TermQuery(new Term(TAG, "lucene"))));
      assertEquals(
          "[>lucene<]", snippet(searcher, highlighter, new PrefixQuery(new Term(TAG, "luc"))));
    }
  }

  private static String snippet(IndexSearcher searcher, MatchHighlighter highlighter, Query query)
      throws IOException {
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.totalHits.value());
    return highlighter
        .highlight(topDocs, query)
        .map(h -> String.valueOf(h.fields.get(TAG)))
        .collect(Collectors.joining());
  }
}
