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
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.QueryBuilder;

/**
 * LengthGoalBreakIterator "skip[s] past breaks that would result in passages that are too short";
 * on a multi-valued field the UnifiedHighlighter runs it on each value separately
 * (SplittingBreakIterator: the enclosed iterator never sees the value separator). A passage in a
 * later value must therefore start inside that value, exactly as with the base iterator when every
 * sentence is already longer than the length goal.
 */
public class TestLengthGoalMultiValueSeparator extends LuceneTestCase {

  private static String highlight(
      IndexSearcher searcher, Analyzer analyzer, Query query, TopDocs topDocs, boolean lengthGoal)
      throws Exception {
    UnifiedHighlighter highlighter =
        UnifiedHighlighter.builder(searcher, analyzer)
            .withBreakIterator(
                () ->
                    lengthGoal
                        ? LengthGoalBreakIterator.createMinLength(
                            BreakIterator.getSentenceInstance(Locale.ROOT), 10, 0.5f)
                        : BreakIterator.getSentenceInstance(Locale.ROOT))
            .build();
    return highlighter.highlight("body", query, topDocs, 2)[0];
  }

  public void testPassageInSecondValueDoesNotStartAtTheValueSeparator() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    // each value is one sentence longer than the length goal (10)
    doc.add(new TextField("body", "First value here.", Field.Store.YES));
    doc.add(new TextField("body", "Second value here.", Field.Store.YES));
    iw.addDocument(doc);
    iw.close();
    try (IndexReader ir = DirectoryReader.open(dir)) {
    IndexSearcher searcher = new IndexSearcher(ir);

    for (String q : new String[] {"second", "first second"}) {
      Query query = new QueryBuilder(analyzer).createBooleanQuery("body", q);
      TopDocs topDocs = searcher.search(query, 10);
      assertEquals(1, topDocs.scoreDocs.length);
      String base = highlight(searcher, analyzer, query, topDocs, false);
      String withGoal = highlight(searcher, analyzer, query, topDocs, true);
      String shown = withGoal.replace("\u0000", "\\u0000");
      assertFalse(
          "query '" + q + "': the passage starts at the value separator: " + shown,
          withGoal.contains("\u0000"));
      assertEquals("query '" + q + "'", base, withGoal);
    }

    } finally {
      dir.close();
    }
  }
}
