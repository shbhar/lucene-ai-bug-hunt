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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNegativeMaxNoHighlightPassages extends LuceneTestCase {

  public void testMinusTwoDefaultsToMaxPassages() throws Exception {
    StandardAnalyzer analyzer = new StandardAnalyzer();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("body", "First sentence. Second sentence.", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(r);
        Query query = new MatchAllDocsQuery(); // no match in "body"
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, analyzer).withMaxNoHighlightPassages(-2).build();
        String[] snippets;
        try {
          snippets = highlighter.highlight("body", query, searcher.search(query, 1), 1);
        } catch (IllegalArgumentException e) {
          throw new AssertionError(
              "maxNoHighlightPassages -2 (< 0): expected the default summary, got " + e, e);
        }
        assertEquals("First sentence. ", snippets[0]);
      }
    }
  }
}
