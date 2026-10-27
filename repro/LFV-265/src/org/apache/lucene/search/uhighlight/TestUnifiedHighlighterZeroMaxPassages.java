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
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * highlight(field, query, topDocs, maxPassages) does not validate maxPassages. With 0, a document
 * without matches yields a null snippet, but a document with a match throws NullPointerException
 * from FieldHighlighter#maybeAddPassage (it peeks the empty passage queue).
 */
public class TestUnifiedHighlighterZeroMaxPassages extends LuceneTestCase {

  public void testZeroMaxPassagesWithMatch() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("body", "alpha beta. gamma alpha.", TextField.TYPE_STORED));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();

        // control: a query matching nothing in the field gives a null snippet with maxPassages=0
        TermQuery noMatch = new TermQuery(new Term("body", "delta"));
        String[] none =
            highlighter.highlightFields(new String[] {"body"}, noMatch, new int[] {0}, new int[] {0})
                .get("body");
        assertNull(none[0]);

        TermQuery query = new TermQuery(new Term("body", "alpha"));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);
        String[] snippets;
        try {
          snippets = highlighter.highlight("body", query, topDocs, 0);
        } catch (IllegalArgumentException expectedRejection) {
          return; // rejecting maxPassages=0 up front would also be correct
        }
        assertEquals(1, snippets.length);
        assertNull("no passage may be returned when maxPassages is 0", snippets[0]);
      }
    }
  }
}
