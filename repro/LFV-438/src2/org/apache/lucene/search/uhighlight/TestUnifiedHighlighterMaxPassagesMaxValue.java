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
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * UnifiedHighlighter#highlight(field, query, topDocs, maxPassages) with maxPassages =
 * Integer.MAX_VALUE ("no limit") fails for every document that matches: FieldHighlighter sizes its
 * passage queue with Math.min(64, maxPassages + 1), which wraps to Integer.MIN_VALUE.
 */
public class TestUnifiedHighlighterMaxPassagesMaxValue extends LuceneTestCase {

  private String[] highlight(int maxPassages) throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", "This is a test. Just a test highlighting.", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        TermQuery query = new TermQuery(new Term("body", "highlighting"));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.totalHits.value);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        return highlighter.highlight("body", query, topDocs, maxPassages);
      }
    }
  }

  /** Control: a large but not overflowing limit works. */
  public void testLargeMaxPassages() throws Exception {
    String[] snippets = highlight(1000);
    assertEquals(1, snippets.length);
    assertEquals("Just a test <b>highlighting</b>.", snippets[0]);
  }

  public void testMaxValueMaxPassages() throws Exception {
    String[] snippets;
    try {
      snippets = highlight(Integer.MAX_VALUE);
    } catch (IllegalArgumentException e) {
      throw new AssertionError("highlight(..., Integer.MAX_VALUE) threw", e);
    }
    assertEquals(1, snippets.length);
    assertEquals("Just a test <b>highlighting</b>.", snippets[0]);
  }
}
