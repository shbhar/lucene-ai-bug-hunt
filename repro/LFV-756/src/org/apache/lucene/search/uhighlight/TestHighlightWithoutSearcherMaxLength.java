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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UnifiedHighlighter#getMaxLength: "The maximum content size to process. Content will be truncated
 * to this size before highlighting." highlightWithoutSearcher does not truncate: it analyzes and
 * highlights the whole content, so a match after maxLength is highlighted and returned, while the
 * same text highlighted through a searcher is cut at maxLength first.
 */
public class TestHighlightWithoutSearcherMaxLength extends LuceneTestCase {

  private static final String TEXT = "Alpha beta gamma. Delta epsilon. Lucene is here.";
  private static final int MAX_LENGTH = 20; // the only match ("Lucene", offset 33) is after it

  public void testHighlightWithoutSearcherHonoursMaxLength() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    Query query = new TermQuery(new Term("body", "lucene"));

    // Control 1: the same text through an index and a searcher is truncated to maxLength.
    String withSearcher;
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", TEXT, Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.totalHits.value());
        UnifiedHighlighter uh =
            UnifiedHighlighter.builder(searcher, analyzer).withMaxLength(MAX_LENGTH).build();
        withSearcher = uh.highlight("body", query, hits, 1)[0];
      }
    }
    assertFalse(withSearcher, withSearcher.contains("<b>"));
    assertTrue(withSearcher, withSearcher.length() <= MAX_LENGTH);

    // Control 2: without a searcher and with maxLength above the text length, the match is found.
    UnifiedHighlighter big =
        UnifiedHighlighter.builderWithoutSearcher(analyzer).withMaxLength(1000).build();
    assertEquals("<b>Lucene</b> is here.", big.highlightWithoutSearcher("body", query, TEXT, 1));

    // The claim: without a searcher, maxLength must still truncate the content first.
    UnifiedHighlighter small =
        UnifiedHighlighter.builderWithoutSearcher(analyzer).withMaxLength(MAX_LENGTH).build();
    assertEquals(MAX_LENGTH, small.getMaxLength());
    Object withoutSearcher = small.highlightWithoutSearcher("body", query, TEXT, 1);
    String msg = "maxLength=" + MAX_LENGTH + " with searcher: '" + withSearcher
        + "' without searcher: '" + withoutSearcher + "'";
    System.out.println(msg);
    assertEquals(msg, withSearcher, withoutSearcher);
  }
}
