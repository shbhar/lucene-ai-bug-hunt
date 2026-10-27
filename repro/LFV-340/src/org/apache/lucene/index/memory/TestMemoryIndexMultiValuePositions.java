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
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * MemoryIndex is documented and tested (TestMemoryIndexAgainstDirectory) to index a document like a
 * one-document IndexWriter index. For a field with several values, IndexWriter carries the final
 * position increment and final offset that each value's TokenStream reports in end() (for example
 * the hole left by a trailing stop word) and applies the analyzer's position/offset gaps even after
 * a value that produced no tokens. MemoryIndex drops both, so positions, offsets and phrase matches
 * differ from the real index.
 */
public class TestMemoryIndexMultiValuePositions extends LuceneTestCase {

  /** Lower-cases and removes "the"; optional gaps between values of the same field. */
  private static Analyzer stopAnalyzer(int positionGap, int offsetGap) {
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

  private static FieldType textWithOffsets() {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    return ft;
  }

  /** "pos@start-end" for the single occurrence of term in field. */
  private static String occurrence(LeafReader reader, String field, String term) throws IOException {
    TermsEnum te = reader.terms(field).iterator();
    assertTrue(te.seekExact(new BytesRef(term)));
    PostingsEnum pe = te.postings(null, PostingsEnum.OFFSETS);
    assertEquals(0, pe.nextDoc());
    int pos = pe.nextPosition();
    return pos + "@" + pe.startOffset() + "-" + pe.endOffset();
  }

  private Directory indexWithIndexWriter(Document doc, Analyzer analyzer) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec());
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(doc);
    }
    return dir;
  }

  /** A value that ends in a stop word: the hole it leaves must separate it from the next value. */
  public void testTrailingStopWordBetweenValues() throws IOException {
    Analyzer analyzer = stopAnalyzer(0, 1);
    Document doc = new Document();
    doc.add(new Field("body", "quick the", textWithOffsets()));
    doc.add(new Field("body", "fox", textWithOffsets()));

    try (Directory dir = indexWithIndexWriter(doc, analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader iw = reader.leaves().get(0).reader();
      LeafReader mi =
          (LeafReader) MemoryIndex.fromDocument(doc, analyzer, true, false).createSearcher().getIndexReader();

      // IndexWriter: quick@0, hole at 1 (removed "the"), fox@2
      assertEquals("0@0-5", occurrence(iw, "body", "quick"));
      assertEquals("2@10-13", occurrence(iw, "body", "fox"));
      PhraseQuery quickFox = new PhraseQuery("body", "quick", "fox");
      assertEquals(0, new IndexSearcher(iw).count(quickFox));

      // MemoryIndex must agree
      assertEquals("position/offset of 'fox'", occurrence(iw, "body", "fox"), occurrence(mi, "body", "fox"));
      assertEquals("phrase \"quick fox\" hit count", 0, new IndexSearcher(mi).count(quickFox));
    }
  }

  /** A first value that analyzes to nothing still advances positions and offsets in IndexWriter. */
  public void testLeadingValueWithoutTokens() throws IOException {
    Analyzer analyzer = stopAnalyzer(100, 1);
    Document doc = new Document();
    doc.add(new Field("body", "the", textWithOffsets()));
    doc.add(new Field("body", "quick fox", textWithOffsets()));

    try (Directory dir = indexWithIndexWriter(doc, analyzer);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      LeafReader iw = reader.leaves().get(0).reader();
      LeafReader mi =
          (LeafReader) MemoryIndex.fromDocument(doc, analyzer, true, false).createSearcher().getIndexReader();

      // IndexWriter: -1 + 1 (hole of "the") + 100 (gap) + 1 = 101; offsets after "the" + offset gap 1
      assertEquals("101@4-9", occurrence(iw, "body", "quick"));
      assertEquals("position/offset of 'quick'", occurrence(iw, "body", "quick"), occurrence(mi, "body", "quick"));
    }
  }
}
