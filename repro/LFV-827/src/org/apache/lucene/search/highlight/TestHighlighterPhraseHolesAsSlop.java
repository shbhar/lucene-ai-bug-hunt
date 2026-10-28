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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.MatchesIterator;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * The classic Highlighter (QueryScorer / WeightedSpanTermExtractor) converts an exact PhraseQuery
 * with a position hole (a removed stop word) to an ordered SpanNearQuery whose slop is the number
 * of holes. That span query also accepts the terms with fewer words between them, and for each
 * start it reports only the shortest span, so the highlighter marks occurrences that do not match
 * the phrase and can skip the occurrence that does.
 */
public class TestHighlighterPhraseHolesAsSlop extends LuceneTestCase {

  private static final String FIELD = "f";

  public void testExactPhraseWithStopWordHole() throws Exception {
    Analyzer analyzer =
        new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true, MockTokenFilter.ENGLISH_STOPSET);
    // "rock and roll" -> rock at position 0, roll at position 2 ("and" is a stop word)
    Query query = new QueryBuilder(analyzer).createPhraseQuery(FIELD, "rock and roll");
    assertTrue(query instanceof PhraseQuery);
    assertArrayEquals(new int[] {0, 2}, ((PhraseQuery) query).getPositions());
    assertEquals(0, ((PhraseQuery) query).getSlop());

    String[] texts = {"rock roll rock and roll", "rock roll roll"};
    // Weight#matches (checked below): the phrase matches only at positions 2-4 in the first text and
    // only at positions 0-2 (rock + the second roll) in the second text
    int[][] matchPositions = {{2, 4}, {0, 2}};

    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    for (String text : texts) {
      Document doc = new Document();
      doc.add(new Field(FIELD, text, ft));
      w.addDocument(doc);
    }
    w.close();
    IndexReader reader = DirectoryReader.open(dir);
    IndexSearcher searcher = newSearcher(reader);
    assertEquals(2, searcher.count(query));
    Weight weight = searcher.createWeight(searcher.rewrite(query), ScoreMode.COMPLETE_NO_SCORES, 1);
    for (int doc = 0; doc < texts.length; doc++) {
      Matches matches = weight.matches(reader.leaves().get(0), doc);
      assertNotNull(matches);
      MatchesIterator it = matches.getMatches(FIELD);
      assertTrue(it.next());
      assertEquals(matchPositions[doc][0], it.startPosition());
      assertEquals(matchPositions[doc][1], it.endPosition());
      assertFalse("one phrase match per document", it.next());
    }
    reader.close();
    dir.close();

    String[] actual = new String[texts.length];
    for (int doc = 0; doc < texts.length; doc++) {
      Highlighter highlighter =
          new Highlighter(new SimpleHTMLFormatter(), new QueryScorer(query, FIELD));
      highlighter.setTextFragmenter(new NullFragmenter());
      actual[doc] = highlighter.getBestFragment(analyzer, FIELD, texts[doc]);
    }
    StringBuilder problems = new StringBuilder();
    // 1) the adjacent pair "rock roll" at positions 0-1 does not match the phrase
    if (actual[0].equals("rock roll <B>rock</B> and <B>roll</B>") == false) {
      problems.append("text 1: expected only the phrase match highlighted, got: ").append(actual[0]);
    }
    // 2) the "roll" that completes the match (the last word) must be highlighted
    if (actual[1].endsWith("<B>roll</B>") == false) {
      problems.append(" | text 2: matching last word not highlighted, got: ").append(actual[1]);
    }
    assertEquals("", problems.toString());
  }
}
