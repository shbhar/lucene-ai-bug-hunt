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
package org.apache.lucene.index.memory;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMemoryIndexValueEndHole extends LuceneTestCase {

  public void testTrailingStopWordHoleBetweenValues() throws Exception {
    Analyzer analyzer = new StandardAnalyzer(new CharArraySet(List.of("the"), false));
    Document doc = new Document();
    doc.add(new TextField("body", "quick the", Field.Store.NO));
    doc.add(new TextField("body", "fox", Field.Store.NO));
    PhraseQuery quickFox = new PhraseQuery("body", "quick", "fox");

    int indexWriterHits;
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        indexWriterHits = new IndexSearcher(reader).count(quickFox);
      }
    }
    int memoryIndexHits =
        MemoryIndex.fromDocument(doc, analyzer).createSearcher().count(quickFox);

    // IndexWriter keeps the removed "the" as a hole: fox at 2, 0 hits; MemoryIndex: fox at 1, 1 hit
    assertEquals(
        "hits of \"quick fox\", IndexWriter vs MemoryIndex", indexWriterHits, memoryIndexHits);
  }
}
