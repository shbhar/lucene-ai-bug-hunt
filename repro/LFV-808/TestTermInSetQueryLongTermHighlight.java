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

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A TermInSetQuery of "short" and a 1001-byte term finds the document, but a UnifiedHighlighter
 * without WEIGHT_MATCHES fails on it: TermInSetQuery#visit gives the highlighter an automaton
 * supplier that rejects terms longer than Automata.MAX_STRING_UNION_TERM_LENGTH (1000 bytes).
 */
public class TestTermInSetQueryLongTermHighlight extends LuceneTestCase {

  public void testHighlightWithoutWeightMatches() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED);
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", "a short text", withOffsets));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query =
            new TermInSetQuery(
                "body", List.of(new BytesRef("short"), new BytesRef("x".repeat(1001))));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length); // the query itself works
        UnifiedHighlighter highlighter =
            UnifiedHighlighter.builder(searcher, analyzer).withWeightMatches(false).build();
        assertArrayEquals(
            new String[] {"a <b>short</b> text"}, highlighter.highlight("body", query, topDocs));
      }
    }
  }
}
