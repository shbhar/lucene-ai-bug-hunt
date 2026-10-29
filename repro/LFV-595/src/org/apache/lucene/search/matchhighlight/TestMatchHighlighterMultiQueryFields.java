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
package org.apache.lucene.search.matchhighlight;

import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMatchHighlighterMultiQueryFields extends LuceneTestCase {

  public void testLaterQueryOnAnotherFieldIsHighlighted() throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new TextField("title", "foo", Field.Store.YES));
        doc.add(new TextField("body", "bar", Field.Store.YES));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        MatchHighlighter highlighter =
            new MatchHighlighter(searcher, analyzer)
                .appendFieldHighlighter(
                    FieldValueHighlighters.highlighted(
                        80, 1, new PassageFormatter("...", ">", "<"), _ -> true));
        TopDocs topDocs = searcher.search(new MatchAllDocsQuery(), 1);
        // expected: each query's field highlighted; actual on main: {title=[>foo<]}, body dropped
        assertEquals(
            Map.of("title", List.of(">foo<"), "body", List.of(">bar<")),
            highlighter
                .highlight(
                    topDocs,
                    new TermQuery(new Term("title", "foo")),
                    new TermQuery(new Term("body", "bar")))
                .findFirst()
                .orElseThrow()
                .fields);
      }
    }
  }
}
