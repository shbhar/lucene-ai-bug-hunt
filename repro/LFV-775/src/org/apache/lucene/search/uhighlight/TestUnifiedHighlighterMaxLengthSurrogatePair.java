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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.UnicodeUtil;

public class TestUnifiedHighlighterMaxLengthSurrogatePair extends LuceneTestCase {

  public void testFirstValueCutInsideSurrogatePair() throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    FieldType offsetsInPostings = new FieldType(TextField.TYPE_STORED);
    offsetsInPostings.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", "Hello \uD83D\uDE00 world", offsetsInPostings));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query query = new TermQuery(new Term("body", "hello"));
        String snippet =
            UnifiedHighlighter.builder(searcher, analyzer)
                .withMaxLength(7)
                .build()
                .highlight("body", query, searcher.search(query, 1))[0];

        // expected whole characters only (maxLength 6 gives "<b>Hello</b> "); actual ends in U+D83D
        assertTrue(
            "expected a well-formed snippet, but was: " + snippet.replace("\uD83D", "\\uD83D"),
            UnicodeUtil.validUTF16String(snippet));
      }
    }
  }
}
