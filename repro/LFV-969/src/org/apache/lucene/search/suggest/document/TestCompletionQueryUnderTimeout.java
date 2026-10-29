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
package org.apache.lucene.search.suggest.document;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestCompletionQueryUnderTimeout extends LuceneTestCase {

  public void testNeverFiringTimeoutReturnsSuggestionOnce() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        doc.add(new SuggestField("sug", "apple", 7));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(r);
        searcher.setTimeout(() -> false);
        TopSuggestDocsCollector collector = new TopSuggestDocsCollector(5, false);
        searcher.search(new PrefixCompletionQuery(analyzer, new Term("sug", "app")), collector);
        // one document with one suggestion: expected 1 hit, actual 5 (the same suggestion repeated)
        assertEquals(1, collector.get().scoreLookupDocs().length);
      }
    }
  }
}
