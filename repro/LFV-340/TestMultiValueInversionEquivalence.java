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
import java.util.TreeMap;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.StopFilter;
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
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.index.Term;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.tests.analysis.MockTokenFilter;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Independent check: a document with a multi-valued text field must be inverted by MemoryIndex
 * exactly as by IndexWriter (one-document index), same analyzer, same Document. MemoryIndex
 * documents itself as a substitute for a RAM-resident Directory, and addField says it is
 * "Equivalent to adding" a Lucene Field. Only public API is used.
 */
public class TestMultiValueInversionEquivalence extends LuceneTestCase {

  private static final String F = "body";

  /** StandardTokenizer + StopFilter({"the"}) with configurable gaps. */
  private static Analyzer analyzer(int posGap, int offGap) {
    final CharArraySet stop = new CharArraySet(List.of("the"), false);
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new StandardTokenizer();
        return new TokenStreamComponents(t, new StopFilter(t, stop));
      }

      @Override
      public int getPositionIncrementGap(String fieldName) {
        return posGap;
      }

      @Override
      public int getOffsetGap(String fieldName) {
        return offGap;
      }
    };
  }

  private static Document doc(String... values) {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
    ft.freeze();
    Document d = new Document();
    for (String v : values) {
      d.add(new Field(F, v, ft));
    }
    return d;
  }

  /** term -> "pos@start-end ..." for the single document of the reader. */
  private static String dump(LeafReader r) throws IOException {
    TreeMap<String, String> m = new TreeMap<>();
    Terms terms = r.terms(F);
    if (terms != null) {
      TermsEnum te = terms.iterator();
      BytesRef t;
      while ((t = te.next()) != null) {
        PostingsEnum pe = te.postings(null, PostingsEnum.ALL);
        StringBuilder sb = new StringBuilder();
        while (pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
          for (int i = 0; i < pe.freq(); i++) {
            int p = pe.nextPosition();
            sb.append(p).append('@').append(pe.startOffset()).append('-').append(pe.endOffset());
            sb.append(' ');
          }
        }
        m.put(t.utf8ToString(), sb.toString().trim());
      }
    }
    return m.toString();
  }

  /** Compares IndexWriter vs MemoryIndex postings and, optionally, a query's hit count. */
  private static void check(Analyzer a, Query q, String... values) throws IOException {
    Document d = doc(values);
    String iwDump;
    int iwHits = -1;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      w.addDocument(d);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        iwDump = dump(r.leaves().get(0).reader());
        if (q != null) {
          iwHits = new IndexSearcher(r).count(q);
        }
      }
    }
    MemoryIndex mi = MemoryIndex.fromDocument(d, a, true, false);
    IndexSearcher ms = mi.createSearcher();
    String miDump = dump((LeafReader) ms.getIndexReader());
    int miHits = q == null ? -1 : ms.count(q);
    String values0 = String.join(" | ", values);
    System.out.println("values=[" + values0 + "]");
    System.out.println("  IndexWriter : " + iwDump + (q == null ? "" : " hits(" + q + ")=" + iwHits));
    System.out.println("  MemoryIndex : " + miDump + (q == null ? "" : " hits(" + q + ")=" + miHits));
    assertEquals("postings (pos@start-end) for values [" + values0 + "]", iwDump, miDump);
    if (q != null) {
      assertEquals("hit count of " + q + " for values [" + values0 + "]", iwHits, miHits);
    }
  }

  // ---- controls: the oracle must agree when neither trigger is present ----

  public void testControlPlainMultiValue() throws IOException {
    check(analyzer(0, 1), new PhraseQuery(F, "brown", "fox"), "quick brown", "fox jumps");
  }

  public void testControlEmptyMiddleValue() throws IOException {
    check(analyzer(100, 1), null, "quick", "", "fox");
  }

  public void testControlStopWordInsideValue() throws IOException {
    check(analyzer(0, 1), new PhraseQuery(F, "quick", "fox"), "quick the fox");
  }

  // ---- claim 1: final position increment of a non-final value (trailing stop word) ----

  public void testTrailingRemovedTokenGapZero() throws IOException {
    // IndexWriter: quick@0, end() adds the skipped position -> fox@2; phrase "quick fox" no match.
    check(analyzer(0, 1), new PhraseQuery(F, "quick", "fox"), "quick the", "fox");
  }

  // ---- claim 2: leading value(s) without tokens ----

  public void testLeadingStopOnlyValue() throws IOException {
    // IndexWriter: "the" -> position -1+1=0, offset 3; gaps -> 100 / 4; quick@101 4-9.
    check(analyzer(100, 1), null, "the", "quick fox");
  }

  public void testLeadingEmptyStringValue() throws IOException {
    // No stop word involved: an empty first value still gets the position gap in IndexWriter.
    check(analyzer(100, 1), null, "", "quick fox");
  }

  // ---- upstream contract: TestIndexWriter#testStopwordsPosIncHole (LUCENE-3849), run on MemoryIndex ----

  public void testUpstreamStopwordsPosIncHoleOnMemoryIndex() throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tokenizer = new MockTokenizer();
            TokenStream stream = new MockTokenFilter(tokenizer, MockTokenFilter.ENGLISH_STOPSET);
            return new TokenStreamComponents(tokenizer, stream);
          }
        };
    Document d = new Document();
    d.add(new TextField(F, "just a", Field.Store.NO));
    d.add(new TextField(F, "test of gaps", Field.Store.NO));
    PhraseQuery.Builder b = new PhraseQuery.Builder();
    b.add(new Term(F, "just"), 0);
    b.add(new Term(F, "test"), 2);
    PhraseQuery pq = b.build(); // body:"just ? test"

    int iwHits;
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      w.addDocument(d);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        iwHits = new IndexSearcher(r).count(pq);
      }
    }
    MemoryIndex mi = new MemoryIndex();
    for (org.apache.lucene.index.IndexableField f : d) {
      mi.addField(f, a);
    }
    int miHits = mi.createSearcher().count(pq);
    System.out.println("upstream LUCENE-3849 case: IndexWriter hits=" + iwHits + " MemoryIndex hits=" + miHits);
    assertEquals("IndexWriter side (asserted upstream in TestIndexWriter)", 1, iwHits);
    assertEquals("MemoryIndex hits for " + pq, 1, miHits);
  }
}
