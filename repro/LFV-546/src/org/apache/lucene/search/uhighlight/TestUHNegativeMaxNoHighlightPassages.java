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
 * UnifiedHighlighter#getMaxNoHighlightPassages: "If it's less than 0 (the default) then this
 * defaults to the maxPassages parameter given for each request." Builder#withMaxNoHighlightPassages
 * accepts any int.
 */
public class TestUHNegativeMaxNoHighlightPassages extends LuceneTestCase {

  public void testAnyNegativeValueDefaultsToMaxPassages() throws Exception {
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
      Query query = new TermQuery(new Term("id", "1")); // no match in "body"
      TopDocs topDocs = searcher.search(query, 10);

      String expected =
          UnifiedHighlighter.builder(searcher, analyzer)
              .withMaxNoHighlightPassages(-1)
              .build()
              .highlight("body", query, topDocs, 1)[0];
      assertEquals("First sentence. ", expected);
      String actual =
          UnifiedHighlighter.builder(searcher, analyzer)
              .withMaxNoHighlightPassages(-2)
              .build()
              .highlight("body", query, topDocs, 1)[0];
      assertEquals(expected, actual);
      }
    }
  }
}
