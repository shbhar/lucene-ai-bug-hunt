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
 * When a multi-valued field has no match, the UnifiedHighlighter returns a default summary of the
 * leading passages "as delineated by the BreakIterator" (getMaxNoHighlightPassages javadoc). The
 * internal value separator (MULTIVAL_SEP_CHAR) is not part of any value, and the user's
 * BreakIterator never sees it (SplittingBreakIterator javadoc), so it must not be returned as one of
 * those passages.
 */
public class TestUHSummaryMultiValueSeparator extends LuceneTestCase {

  public void testSummaryOfMultiValuedFieldDoesNotReturnTheValueSeparator() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    doc.add(new TextField("body", "First value.", Field.Store.YES));
    doc.add(new TextField("body", "Second value.", Field.Store.YES));
    doc.add(new StringField("id", "1", Field.Store.NO));
    iw.addDocument(doc);
    iw.close();
    try (IndexReader ir = DirectoryReader.open(dir)) {
    IndexSearcher searcher = new IndexSearcher(ir);

    // the query matches the document but nothing in "body": the default summary is returned
    Query query = new TermQuery(new Term("id", "1"));
    TopDocs topDocs = searcher.search(query, 10);
    assertEquals(1, topDocs.scoreDocs.length);
    UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();

    // maxPassages = 2 and maxNoHighlightPassages = -1 (default): the two leading passages are the
    // two values' sentences.
    String snippet = highlighter.highlight("body", query, topDocs, 2)[0];
    String shown = snippet.replace("\u0000", "\\u0000");
    assertFalse("the value separator is returned in the summary: " + shown, snippet.contains("\u0000"));
    assertTrue("the second value is missing from a 2-passage summary: " + shown, snippet.contains("Second value."));

    } finally {
      dir.close();
    }
  }

  public void testSummaryWithMorePassagesThanSentencesHasNoSeparator() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    Directory dir = newDirectory();
    IndexWriter iw = new IndexWriter(dir, newIndexWriterConfig(analyzer));
    Document doc = new Document();
    doc.add(new TextField("body", "First value.", Field.Store.YES));
    doc.add(new TextField("body", "Second value.", Field.Store.YES));
    doc.add(new StringField("id", "1", Field.Store.NO));
    iw.addDocument(doc);
    iw.close();
    try (IndexReader ir = DirectoryReader.open(dir)) {
    IndexSearcher searcher = new IndexSearcher(ir);
    Query query = new TermQuery(new Term("id", "1"));
    TopDocs topDocs = searcher.search(query, 10);
    UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();

    // for highlighted values the formatter separates values with its ellipsis
    // (TestUnifiedHighlighter#testMaxLengthWithMultivalue); the summary contains the raw separator
    String snippet = highlighter.highlight("body", query, topDocs, 3)[0];
    assertFalse(
        "the value separator is returned in the summary: " + snippet.replace("\u0000", "\\u0000"),
        snippet.contains("\u0000"));

    } finally {
      dir.close();
    }
  }
}
