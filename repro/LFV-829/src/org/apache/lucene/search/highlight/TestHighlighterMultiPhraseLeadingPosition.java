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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.MultiPhraseQuery;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockSynonymFilter;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * The classic Highlighter (QueryScorer / WeightedSpanTermExtractor) converts a MultiPhraseQuery to
 * a SpanNearQuery whose slop includes every unused position below the first query position. A
 * MultiPhraseQuery whose positions start at 1 (what QueryBuilder produces for a quoted query that
 * starts with a stop word and contains a synonym) is therefore highlighted as if it had slop 1, and
 * non-adjacent occurrences that do not match the phrase get highlighted.
 */
public class TestHighlighterMultiPhraseLeadingPosition extends LuceneTestCase {

  private static final String FIELD = "f";
  private static final String TEXT = "dogs often bark but my dogs bark";

  private static String highlight(Query query) throws Exception {
    Highlighter highlighter =
        new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(query, FIELD));
    highlighter.setTextFragmenter(new NullFragmenter());
    MockAnalyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    return highlighter.getBestFragment(analyzer, FIELD, TEXT);
  }

  public void testLeadingPositionIsNotAGap() throws Exception {
    // query analyzer: stop words removed (position increments kept) plus a synonym dogs -> dog
    Analyzer queryAnalyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer(MockTokenizer.WHITESPACE, false);
            TokenStream stream = new MockTokenFilter(tokenizer, MockTokenFilter.ENGLISH_STOPSET);
            return new TokenStreamComponents(tokenizer, new MockSynonymFilter(stream));
          }
        };
    Query query = new QueryBuilder(queryAnalyzer).createPhraseQuery(FIELD, "the dogs bark");
    assertTrue(query instanceof MultiPhraseQuery);
    assertEquals(1, ((MultiPhraseQuery) query).getPositions()[0]);

    Directory dir = newDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false)));
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    Document doc = new Document();
    doc.add(new Field(FIELD, TEXT, ft));
    w.addDocument(doc);
    w.close();
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    assertEquals(1, searcher.count(query));

    // Reference: the phrase matches once, "dogs bark" at positions 5-6 (offsets 23-32)
    Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1);
    Matches matches = weight.matches(reader.leaves().get(0), 0);
    assertNotNull(matches);
    MatchesIterator it = matches.getMatches(FIELD);
    assertTrue(it.next());
    assertEquals(5, it.startPosition());
    assertEquals(6, it.endPosition());
    assertEquals(23, it.startOffset());
    assertEquals(32, it.endOffset());
    assertFalse(it.next());
    reader.close();
    dir.close();

    String expected = "dogs often bark but my <B>dogs</B> <B>bark</B>";
    // control 1: the PhraseQuery with the same positions (1, 2) is highlighted correctly
    Query phrase =
        new PhraseQuery.Builder()
            .add(new Term(FIELD, "dogs"), 1)
            .add(new Term(FIELD, "bark"), 2)
            .build();
    assertEquals(expected, highlight(phrase));
    // control 2: the same MultiPhraseQuery starting at position 0 is highlighted correctly
    Query mpq0 =
        new MultiPhraseQuery.Builder()
            .add(new Term[] {new Term(FIELD, "dogs"), new Term(FIELD, "dog")}, 0)
            .add(new Term[] {new Term(FIELD, "bark")}, 1)
            .build();
    assertEquals(expected, highlight(mpq0));
    // the MultiPhraseQuery from QueryBuilder (positions 1, 2)
    assertEquals(expected, highlight(query));
  }
}
