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
import java.util.Locale;
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
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * LengthGoalBreakIterator with a length goal of 0 ("at least 0 apart" / "closest to 0") highlights
 * matches like its base iterator (TestLengthGoalBreakIterator uses goal 0 for highlights), but the
 * default summary of a document without a match throws IllegalArgumentException.
 */
public class TestLengthGoalZeroGoalSummary extends LuceneTestCase {

  public void testZeroLengthGoalSummary() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory()) {
      IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer));
      Document doc = new Document();
      doc.add(new TextField("body", "First sentence. Second sentence.", Field.Store.YES));
      doc.add(new StringField("id", "1", Field.Store.NO));
      iw.addDocument(doc);
      iw.close();
      try (IndexReader ir = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(ir);

      for (boolean min : new boolean[] {true, false}) {
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withBreakIterator(
                    () -> {
                      BreakIterator base = BreakIterator.getSentenceInstance(Locale.ROOT);
                      return min
                          ? LengthGoalBreakIterator.createMinLength(base, 0, 0.5f)
                          : LengthGoalBreakIterator.createClosestToLength(base, 0, 0.5f);
                    })
                .build();
        // goal 0 is accepted for highlighting a match
        Query match = new TermQuery(new Term("body", "second"));
        assertEquals(
            "First sentence. <b>Second</b> sentence.".substring(16),
            highlighter.highlight("body", match, searcher.search(match, 10), 1)[0]);
        // and the summary of a document without a match in "body"
        Query noMatch = new TermQuery(new Term("id", "1"));
        TopDocs topDocs = searcher.search(noMatch, 10);
        assertEquals(
            (min ? "min" : "closest") + " goal 0",
            "First sentence. ",
            highlighter.highlight("body", noMatch, topDocs, 1)[0]);
      }
      }
    }
  }
}
