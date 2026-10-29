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

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
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
 * {@link UnifiedHighlighter#getMaxLength()}: "Content will be truncated to this size before
 * highlighting." The searcher entry points do that; {@link
 * UnifiedHighlighter#highlightWithoutSearcher} highlights the whole text it is given.
 */
public class TestHighlightWithoutSearcherMaxLength extends LuceneTestCase {

  // 48 chars; the only match, "Lucene", is at offset 33
  private static final String SHORT_TEXT = "Alpha beta gamma. Delta epsilon. Lucene is here.";

  private Analyzer analyzer;
  private Query query;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    query = new TermQuery(new Term("body", "lucene"));
  }

  @Override
  public void tearDown() throws Exception {
    analyzer.close();
    super.tearDown();
  }

  /** The same text and query highlighted through an index and a searcher (null: default). */
  private String highlightWithSearcher(String text, Integer maxLength) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", text, Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value());
        UnifiedHighlighter.Builder builder = UnifiedHighlighter.builder(searcher, analyzer);
        if (maxLength != null) {
          builder.withMaxLength(maxLength);
        }
        UnifiedHighlighter uh = builder.build();
        return uh.highlight("body", query, hits, 1)[0];
      }
    }
  }

  public void testWithMaxLength() throws Exception {
    String withSearcher = highlightWithSearcher(SHORT_TEXT, 20);
    assertEquals("Alpha beta gamma. ", withSearcher); // cut to 20 chars: no match left

    UnifiedHighlighter noSearcher =
        UnifiedHighlighter.builderWithoutSearcher(analyzer).withMaxLength(20).build();
    assertEquals(20, noSearcher.getMaxLength());
    Object withoutSearcher = noSearcher.highlightWithoutSearcher("body", query, SHORT_TEXT, 1);
    // fails today: "<b>Lucene</b> is here." (the match at offset 33 is highlighted)
    assertEquals(withSearcher, withoutSearcher);
  }

  public void testDefaultMaxLength() throws Exception {
    StringBuilder sb = new StringBuilder();
    while (sb.length() < 10_500) {
      sb.append("Alpha beta gamma. ");
    }
    String text = sb.append("Lucene is here.").toString(); // 10,527 chars, match at 10,512
    assertTrue(text.indexOf("Lucene") >= UnifiedHighlighter.DEFAULT_MAX_LENGTH);

    String withSearcher = highlightWithSearcher(text, null);
    assertEquals("Alpha beta gamma. ", withSearcher); // cut to 10,000 chars: no match left

    UnifiedHighlighter noSearcher = UnifiedHighlighter.builderWithoutSearcher(analyzer).build();
    assertEquals(UnifiedHighlighter.DEFAULT_MAX_LENGTH, noSearcher.getMaxLength());
    Object withoutSearcher = noSearcher.highlightWithoutSearcher("body", query, text, 1);
    // fails today: "<b>Lucene</b> is here." (the match at offset 10,512 is highlighted)
    assertEquals(withSearcher, withoutSearcher);
  }

  public void testShorterThanMaxLengthUnchanged() throws Exception {
    UnifiedHighlighter noSearcher =
        UnifiedHighlighter.builderWithoutSearcher(analyzer).withMaxLength(1000).build();
    assertEquals(
        "<b>Lucene</b> is here.",
        noSearcher.highlightWithoutSearcher("body", query, SHORT_TEXT, 1));
  }
}
