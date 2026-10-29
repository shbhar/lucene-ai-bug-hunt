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

import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Probe (not a repro): what a caller sees when it encodes the snippet to UTF-8, with the default
 * analyzer (StandardAnalyzer) and offsets in postings or plain re-analysis, at maxLength 7.
 */
@SuppressSysoutChecks(bugUrl = "probe prints its observations")
public class TestProbeEncodeSnippet extends LuceneTestCase {
  public void testProbe() throws Exception {
    FieldType postings = new FieldType(TextField.TYPE_STORED);
    postings.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    postings.freeze();
    String value = "Hello 😀 world";
    for (FieldType type : new FieldType[] {postings, TextField.TYPE_STORED}) {
      String snippet = run(type, 7, "hello", value);
      String lenient = new String(snippet.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
      String strict;
      try {
        StandardCharsets.UTF_8.newEncoder().encode(CharBuffer.wrap(snippet));
        strict = "encoded";
      } catch (Exception e) {
        strict = e.getClass().getSimpleName() + ": " + e.getMessage();
      }
      System.out.println(
          "PROBE type="
              + (type == postings ? "POSTINGS" : "ANALYSIS")
              + " analyzer=StandardAnalyzer maxLength=7 snippet='"
              + escape(snippet)
              + "' getBytes(UTF_8) round trip='"
              + escape(lenient)
              + "' strict encoder: "
              + strict);
    }
  }

  private String run(FieldType type, int maxLength, String term, String value) throws Exception {
    try (Analyzer analyzer = new StandardAnalyzer();
        Directory dir = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
        Document doc = new Document();
        doc.add(new Field("body", value, type));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        Query query = new TermQuery(new Term("body", term));
        TopDocs hits = searcher.search(query, 10);
        assertEquals(1, hits.scoreDocs.length);
        return UnifiedHighlighter.builder(searcher, analyzer)
            .withMaxLength(maxLength)
            .build()
            .highlight("body", query, hits)[0];
      }
    }
  }

  private static String escape(String s) {
    StringBuilder sb = new StringBuilder();
    for (char c : s.toCharArray()) {
      sb.append(c >= 0x20 && c < 0x7f ? String.valueOf(c) : String.format(Locale.ROOT, "\\u%04X", (int) c));
    }
    return sb.toString();
  }
}
