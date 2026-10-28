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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.standard.StandardTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCaseJupiter;
import org.apache.lucene.util.BytesRef;
import org.junit.jupiter.api.Test;

/**
 * A document with a multi-valued text field must get the same positions and offsets in a
 * MemoryIndex as in a one-document IndexWriter index. Each test first asserts the IndexWriter
 * value, then that MemoryIndex agrees.
 */
public class TestMemoryIndexMultiValuedPositions extends LuceneTestCaseJupiter {

  /** StandardTokenizer, LowerCaseFilter, StopFilter("the"), and the given gaps. */
  private static Analyzer stopTheAnalyzer(int positionGap, int offsetGap) {
    CharArraySet stop = new CharArraySet(List.of("the"), false);
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer tokenizer = new StandardTokenizer();
        TokenStream ts = new StopFilter(new LowerCaseFilter(tokenizer), stop);
        return new TokenStreamComponents(tokenizer, ts);
      }

      @Override
      public int getPositionIncrementGap(String fieldName) {
        return positionGap;
      }

      @Override
      public int getOffsetGap(String fieldName) {
        return offsetGap;
      }
    };
  }

  private static Document doc(String... values) {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    Document doc = new Document();
    for (String value : values) {
      doc.add(new Field("body", value, ft));
    }
    return doc;
  }

  /** "position@startOffset-endOffset" of the single occurrence of term in field body. */
  private static String occurrence(LeafReader reader, String term) throws IOException {
    TermsEnum te = reader.terms("body").iterator();
    assertTrue(te.seekExact(new BytesRef(term)));
    PostingsEnum pe = te.postings(null, PostingsEnum.OFFSETS);
    assertEquals(0, pe.nextDoc());
    int pos = pe.nextPosition();
    return pos + "@" + pe.startOffset() + "-" + pe.endOffset();
  }

  private interface ReaderCheck {
    void check(LeafReader indexWriter, LeafReader memoryIndex) throws IOException;
  }

  /** Indexes doc with IndexWriter (one document) and with MemoryIndex.fromDocument. */
  private static void compare(Document doc, Analyzer analyzer, ReaderCheck check)
      throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        LeafReader iw = reader.leaves().get(0).reader();
        MemoryIndex mi = MemoryIndex.fromDocument(doc, analyzer, true, false);
        check.check(iw, (LeafReader) mi.createSearcher().getIndexReader());
      }
    }
  }

  private static int count(LeafReader reader, Query query) throws IOException {
    return new IndexSearcher(reader).count(query);
  }

  /** [quick the | fox], gap 0: the hole left by the trailing "the" separates the values. */
  @Test
  public void testTrailingStopWordBetweenValues() throws IOException {
    PhraseQuery quickFox = new PhraseQuery("body", "quick", "fox");
    compare(
        doc("quick the", "fox"),
        stopTheAnalyzer(0, 1),
        (iw, mi) -> {
          assertEquals("2@10-13", occurrence(iw, "fox")); // quick@0, hole at 1, fox@2
          assertEquals(0, count(iw, quickFox));
          assertEquals("position of 'fox'", occurrence(iw, "fox"), occurrence(mi, "fox"));
          assertEquals("hits of " + quickFox, count(iw, quickFox), count(mi, quickFox));
        });
  }

  /** The input of TestIndexWriter#testStopwordsPosIncHole (LUCENE-3849), run on MemoryIndex. */
  @Test
  public void testStopwordsPosIncHoleInput() throws IOException {
    Analyzer analyzer =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer();
            TokenStream stream = new MockTokenFilter(tokenizer, MockTokenFilter.ENGLISH_STOPSET);
            return new TokenStreamComponents(tokenizer, stream);
          }
        };
    Document doc = new Document();
    doc.add(new TextField("body", "just a", Field.Store.NO));
    doc.add(new TextField("body", "test of gaps", Field.Store.NO));
    PhraseQuery.Builder builder = new PhraseQuery.Builder();
    builder.add(new Term("body", "just"), 0);
    builder.add(new Term("body", "test"), 2);
    PhraseQuery justHoleTest = builder.build(); // body:"just ? test"
    compare(
        doc,
        analyzer,
        (iw, mi) -> {
          assertEquals(1, count(iw, justHoleTest)); // asserted upstream for IndexWriter
          assertEquals("hits of " + justHoleTest, count(iw, justHoleTest), count(mi, justHoleTest));
        });
  }

  /** [the | quick fox], gap 100, offset gap 1: a first value with no tokens still advances. */
  @Test
  public void testLeadingValueWithoutTokens() throws IOException {
    compare(
        doc("the", "quick fox"),
        stopTheAnalyzer(100, 1),
        (iw, mi) -> {
          // -1, +1 (hole of "the"), +100 (gap), +1 = 101; offsets after "the" (3) + offset gap 1
          assertEquals("101@4-9", occurrence(iw, "quick"));
          assertEquals(
              "position/offsets of 'quick'", occurrence(iw, "quick"), occurrence(mi, "quick"));
        });
  }
}
