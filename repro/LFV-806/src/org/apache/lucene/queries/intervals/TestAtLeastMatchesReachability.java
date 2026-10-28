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
package org.apache.lucene.queries.intervals;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.uhighlight.UnifiedHighlighter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Reachability of the atLeast Matches failure: a query string parsed by the flexible
 * StandardQueryParser (fn:atLeast with an fn:or source), highlighted with a default
 * UnifiedHighlighter (Weight#matches mode is on by default). Public API only.
 */
public class TestAtLeastMatchesReachability extends LuceneTestCase {

  public void testHighlightParsedAtLeastQuery() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        for (String text : new String[] {"a c b a", "x y z"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.YES));
          w.addDocument(doc);
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q = new StandardQueryParser(analyzer).parse("fn:atLeast(2 a b fn:or(c d))", "f");
        TopDocs hits = searcher.search(q, 10);
        assertEquals(1, searcher.count(q));
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        // on main this throws IllegalStateException (AssertionError with -ea)
        String[] snippets = highlighter.highlight("f", q, hits);
        assertEquals(1, snippets.length);
        assertEquals("<b>a</b> <b>c</b> <b>b</b> <b>a</b>", snippets[0]);
      }
    }
  }
}
