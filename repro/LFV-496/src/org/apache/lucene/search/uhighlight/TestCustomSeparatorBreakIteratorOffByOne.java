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
package org.apache.lucene.search.uhighlight;

import java.text.BreakIterator;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CustomSeparatorBreakIterator breaks "whenever a certain separator is found": the boundaries of
 * "Aa bb. Cc dd. Ee ff" with '.' are 0, 6, 13 and 19 (its next() reports exactly these). Its
 * following(offset) and preceding(offset) must follow the java.text.BreakIterator contract: the
 * first boundary after the offset, and the last boundary before it.
 */
public class TestCustomSeparatorBreakIteratorOffByOne extends LuceneTestCase {
  static final String CONTENT = "Aa bb. Cc dd. Ee ff";

  public void testFollowingAndPrecedingNextToTheSeparator() {
    BreakIterator bi = new CustomSeparatorBreakIterator('.');
    bi.setText(CONTENT);
    // the boundaries, as next() reports them
    assertEquals(0, bi.first());
    assertEquals(6, bi.next());
    assertEquals(13, bi.next());
    assertEquals(19, bi.next());
    assertEquals(BreakIterator.DONE, bi.next());

    assertEquals("following(4)", 6, bi.following(4)); // passes: offset inside a sentence
    assertEquals("following(5): offset of the '.' itself", 6, bi.following(5));
    assertEquals("following(12): offset of the '.' itself", 13, bi.following(12));
    assertEquals("preceding(7)", 6, bi.preceding(7)); // passes
    assertEquals("preceding(6): must be < 6", 0, bi.preceding(6));
    assertEquals("preceding(13): must be < 13", 6, bi.preceding(13));
  }

  public void testNextWithLeadingAndTrailingSeparator() {
    BreakIterator bi = new CustomSeparatorBreakIterator('.');
    bi.setText(".a");
    assertEquals(0, bi.first());
    assertEquals("a leading separator ends the first (one-char) passage", 1, bi.next());

    bi.setText("aa.");
    assertEquals(0, bi.first());
    assertEquals(3, bi.next());
    assertEquals("the end is reported once", BreakIterator.DONE, bi.next());
  }

  /** Through the UnifiedHighlighter: LengthGoalBreakIterator min-length summary. */
  public void testLengthGoalSummaryOvershootsByOneSentence() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory()) {
      IndexSearcher searcher = index(dir, analyzer);
      try (IndexReader ir = searcher.getIndexReader()) {
      Query query = new TermQuery(new Term("id", "1")); // no match in "body": default summary
      TopDocs topDocs = searcher.search(query, 10);
      UnifiedHighlighter highlighter =
          UnifiedHighlighter.builder(searcher, analyzer)
              .withBreakIterator(
                  () ->
                      LengthGoalBreakIterator.createMinLength(
                          new CustomSeparatorBreakIterator('.'), 6, 0.f))
              .build();
      // "Breaks will be at least minLength apart": break 6 is the first break at least 6 from 0
      assertEquals("Aa bb.", highlighter.highlight("body", query, topDocs, 1)[0]);
      }
    }
  }

  /** Through the UnifiedHighlighter with default flags: a phrase whose middle is the separator. */
  public void testPhrasePassageLosesTheStartOfItsSentence() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory()) {
      IndexSearcher searcher = index(dir, analyzer);
      try (IndexReader ir = searcher.getIndexReader()) {
      Query query = new PhraseQuery("body", "dd", "ee"); // matches "dd. Ee" = [10, 16)
      TopDocs topDocs = searcher.search(query, 10);
      assertEquals(1, topDocs.scoreDocs.length);
      UnifiedHighlighter highlighter =
          UnifiedHighlighter.builder(searcher, analyzer)
              .withBreakIterator(() -> new CustomSeparatorBreakIterator('.'))
              .build();
      // the passage starts at the break before the match (6), as for every other match
      assertEquals(" Cc <b>dd. Ee</b> ff", highlighter.highlight("body", query, topDocs, 1)[0]);
      }
    }
  }

  private static IndexSearcher index(Directory dir, Analyzer analyzer) throws Exception {
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    doc.add(new TextField("body", CONTENT, Field.Store.YES));
    doc.add(new StringField("id", "1", Field.Store.NO));
    iw.addDocument(doc);
    iw.close();
    return new IndexSearcher(DirectoryReader.open(dir));
  }
}
