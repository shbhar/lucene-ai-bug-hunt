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
package org.apache.lucene.search.highlight;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A phrase query with slop Integer.MAX_VALUE ("any distance") and a position hole, here the removed
 * stop word "the", matches the document and must be highlighted like any other matching phrase.
 * Public API only.
 */
public class TestHighlighterPhraseHugeSlop extends LuceneTestCase {
  private static final String FIELD = "text";
  private static final String TEXT = "the quick brown fox jumps";
  private static final String HIGHLIGHTED = "the <B>quick</B> brown <B>fox</B> jumps";

  private Analyzer analyzer;
  private Directory directory;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer =
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    directory = newDirectory();
    try (IndexWriter writer = new IndexWriter(directory, newIndexWriterConfig(analyzer))) {
      Document document = new Document();
      // no term vectors: both highlighters re-analyze the stored text
      document.add(new TextField(FIELD, TEXT, Store.YES));
      writer.addDocument(document);
    }
    reader = DirectoryReader.open(directory);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    directory.close();
    analyzer.close();
    super.tearDown();
  }

  /** What a user can type: the parser saturates the slop and the stop word leaves a hole. */
  public void testParsedPhraseWithStopWordAndHugeSlop() throws Exception {
    Query q = new QueryParser(FIELD, analyzer).parse("\"quick the fox\"~3000000000");
    PhraseQuery pq = (PhraseQuery) q;
    assertEquals(Integer.MAX_VALUE, pq.getSlop());
    assertArrayEquals(new int[] {0, 2}, pq.getPositions());
    assertHighlighted(q);
  }

  public void testMultiPhraseWithHoleAndSlopMaxValue() throws Exception {
    Query q =
        new MultiPhraseQuery.Builder()
            .add(new Term[] {new Term(FIELD, "quick")}, 0)
            .add(new Term[] {new Term(FIELD, "fox"), new Term(FIELD, "dog")}, 2)
            .setSlop(Integer.MAX_VALUE)
            .build();
    assertHighlighted(q);
  }

  /** The UnifiedHighlighter without weight matches converts phrases with the same code. */
  public void testUnifiedHighlighterWithoutWeightMatches() throws Exception {
    Query q = phraseWithHole(Integer.MAX_VALUE);
    TopDocs hits = searcher.search(q, 10);
    assertEquals(1, hits.totalHits.value());
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
    assertEquals(
        "the <b>quick</b> brown <b>fox</b> jumps", highlighter.highlight(FIELD, q, hits)[0]);
  }

  /** Control: slop plus the one hole is exactly Integer.MAX_VALUE, which still fits in an int. */
  public void testSlopOneBelowLimit() throws Exception {
    assertHighlighted(phraseWithHole(Integer.MAX_VALUE - 1));
  }

  /** Control: slop Integer.MAX_VALUE on a phrase without a hole. */
  public void testSlopMaxValueWithoutHole() throws Exception {
    assertHighlighted(new QueryParser(FIELD, analyzer).parse("\"quick fox\"~2147483647"));
  }

  /** The phrase "quick ? fox": a hole at position 1. */
  private static Query phraseWithHole(int slop) {
    return new PhraseQuery.Builder()
        .add(new Term(FIELD, "quick"), 0)
        .add(new Term(FIELD, "fox"), 2)
        .setSlop(slop)
        .build();
  }

  private void assertHighlighted(Query q) throws Exception {
    assertEquals("the query matches the document: " + q, 1, searcher.count(q));
    Highlighter highlighter = new Highlighter(new QueryScorer(q, FIELD));
    assertEquals(q.toString(), HIGHLIGHTED, highlighter.getBestFragment(analyzer, FIELD, TEXT));
  }
}
