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

import java.util.ArrayList;
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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermInSetQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * With default flags (WEIGHT_MATCHES on), the UnifiedHighlighter labels every match with a
 * pseudo-term built from the matching query (OffsetsEnum.OfMatchesIteratorWithSubs#queryToTerm:
 * the query's terms joined by spaces, else Query#toString()), and PassageScorer#score puts that
 * label in a BytesRefHash, which rejects anything longer than 32766 bytes. A TermInSetQuery with
 * a few thousand ordinary terms has a longer toString(), so it searches fine but cannot be
 * highlighted.
 */
public class TestUnifiedHighlighterLongMatchLabel extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    FieldType withOffsets = new FieldType(TextField.TYPE_STORED); // highlight() wants offsets
    withOffsets.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    doc.add(new Field("body", "this document mentions term42 once", withOffsets));
    w.addDocument(doc);
    w.close();
    reader = DirectoryReader.open(dir);
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private void assertHighlights(Query query) throws Exception {
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.scoreDocs.length); // the query itself is fine
    UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
    String[] snippets = highlighter.highlight("body", query, topDocs);
    assertArrayEquals(new String[] {"this document mentions <b>term42</b> once"}, snippets);
  }

  /** 4,000 ordinary 5-8 byte terms: toString() is 34,896 bytes. */
  public void testTermInSetQueryWithManyTerms() throws Exception {
    List<BytesRef> terms = new ArrayList<>();
    for (int i = 0; i < 4000; i++) {
      terms.add(new BytesRef("term" + i));
    }
    assertHighlights(new TermInSetQuery("body", terms));
  }

  /** Two terms, one of them at the legal maximum term length (IndexWriter.MAX_TERM_LENGTH). */
  public void testTermInSetQueryWithOneMaxLengthTerm() throws Exception {
    String longTerm = "x".repeat(IndexWriter.MAX_TERM_LENGTH);
    assertHighlights(
        new TermInSetQuery("body", List.of(new BytesRef("term42"), new BytesRef(longTerm))));
  }
}
