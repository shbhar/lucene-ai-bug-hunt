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
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestUnifiedHighlighterLoadFieldValuesCursor extends LuceneTestCase {

  public void testDocIdBeforeFirstCall() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter writer =
          new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("body", "a test", Field.Store.YES));
        writer.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        int[] docIdBeforeFirstCall = new int[1];
        UnifiedHighlighter highlighter =
            new UnifiedHighlighter(
                UnifiedHighlighter.builder(searcher, new MockAnalyzer(random()))) {
              @Override
              protected List<CharSequence[]> loadFieldValues(
                  String[] fields, DocIdSetIterator docIter, int cacheCharsThreshold)
                  throws IOException {
                docIdBeforeFirstCall[0] = docIter.docID();
                return super.loadFieldValues(fields, docIter, cacheCharsThreshold);
              }
            };
        Query query = new TermQuery(new Term("body", "test"));
        String[] snippets = highlighter.highlight("body", query, searcher.search(query, 1));
        assertEquals("a <b>test</b>", snippets[0]);
        // expected -1 (docID() before nextDoc() or advance()); actual 2147483647 (NO_MORE_DOCS)
        assertEquals(-1, docIdBeforeFirstCall[0]);
      }
    }
  }
}
