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
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A multi-valued field indexed with an analyzer whose offset gap is not 1 (Analyzer#getOffsetGap,
 * here 3): the UnifiedHighlighter joins the stored values with a single separator character, so
 * with offsets from postings or term vectors every match in the second value is shifted by
 * (gap - 1) characters, and a match near the end of the last value is dropped. The re-analysis
 * offset source rejects such an analyzer ("offset gap of the provided analyzer should be 1"), the
 * postings and term-vector sources highlight the wrong characters without any error.
 */
public class TestUnifiedHighlighterOffsetGap extends LuceneTestCase {

  private static FieldType postingsWithOffsets() {
    FieldType t = new FieldType(TextField.TYPE_STORED);
    t.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    t.freeze();
    return t;
  }

  private static FieldType termVectorsWithOffsets() {
    FieldType t = new FieldType(TextField.TYPE_STORED);
    t.setStoreTermVectors(true);
    t.setStoreTermVectorPositions(true);
    t.setStoreTermVectorOffsets(true);
    t.freeze();
    return t;
  }

  private void check(FieldType type, int offsetGap, String term, String expected) throws Exception {
    MockAnalyzer analyzer = new MockAnalyzer(random(), MockTokenizer.SIMPLE, true);
    analyzer.setOffsetGap(offsetGap);
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      Document doc = new Document();
      doc.add(new Field("body", "alpha one", type));
      doc.add(new Field("body", "beta two", type));
      writer.addDocument(doc);
      writer.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        UnifiedHighlighter highlighter = UnifiedHighlighter.builder(searcher, analyzer).build();
        Query query = new TermQuery(new Term("body", term));
        TopDocs topDocs = searcher.search(query, 10);
        assertEquals(1, topDocs.scoreDocs.length);
        String[] snippets = highlighter.highlight("body", query, topDocs);
        assertEquals(
            "offsetGap=" + offsetGap + " term=" + term + " type=" + type,
            expected,
            snippets[0]);
      }
    }
  }

  // Control: with the default offset gap of 1 both offset sources highlight the right word.
  public void testDefaultOffsetGapPostings() throws Exception {
    check(postingsWithOffsets(), 1, "beta", "<b>beta</b> two");
    check(postingsWithOffsets(), 1, "two", "beta <b>two</b>");
  }

  public void testDefaultOffsetGapTermVectors() throws Exception {
    check(termVectorsWithOffsets(), 1, "beta", "<b>beta</b> two");
    check(termVectorsWithOffsets(), 1, "two", "beta <b>two</b>");
  }

  // Offset gap 3: the match in the second value must still cover the matched word.
  public void testOffsetGapPostingsSecondValue() throws Exception {
    check(postingsWithOffsets(), 3, "beta", "<b>beta</b> two");
  }

  public void testOffsetGapPostingsLastWord() throws Exception {
    check(postingsWithOffsets(), 3, "two", "beta <b>two</b>");
  }

  public void testOffsetGapTermVectorsSecondValue() throws Exception {
    check(termVectorsWithOffsets(), 3, "beta", "<b>beta</b> two");
  }
}
