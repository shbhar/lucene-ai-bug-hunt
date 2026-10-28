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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * Independent verifier repro: a field whose TokenStream sets TermFrequencyAttribute (for example
 * FeatureField) indexed into a MemoryIndex versus the same one-document index built by
 * IndexWriter. The IndexWriter index is the oracle; each test also asserts the absolute value the
 * TermFrequencyAttribute / FeatureField contract implies, so the oracle itself is checked.
 */
public class TestMemoryIndexCustomTermFreq extends LuceneTestCase {

  /** Emits each term once, setting a custom TermFrequencyAttribute value for it. */
  static final class CustomFreqTokenStream extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private final String[] terms;
    private final int[] freqs;
    private int upto;

    CustomFreqTokenStream(String[] terms, int[] freqs) {
      this.terms = terms;
      this.freqs = freqs;
    }

    @Override
    public boolean incrementToken() {
      if (upto >= terms.length) {
        return false;
      }
      clearAttributes();
      termAtt.append(terms[upto]);
      freqAtt.setTermFrequency(freqs[upto]);
      upto++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  /** What one leaf reader says about one term of one field. */
  static final class Stats {
    long docFreq, totalTermFreq, sumTotalTermFreq, sumDocFreq;
    int postingsFreq;
    long norm = Long.MIN_VALUE; // MIN_VALUE = no norms
    float score;

    @Override
    public String toString() {
      return "docFreq="
          + docFreq
          + " freq="
          + postingsFreq
          + " totalTermFreq="
          + totalTermFreq
          + " sumTotalTermFreq="
          + sumTotalTermFreq
          + " sumDocFreq="
          + sumDocFreq
          + " norm="
          + (norm == Long.MIN_VALUE ? "none" : Long.toString(norm))
          + " score="
          + score;
    }
  }

  private static Stats stats(LeafReader leaf, IndexSearcher searcher, String field, String term, Query q)
      throws IOException {
    Stats s = new Stats();
    Terms terms = leaf.terms(field);
    assertNotNull("no terms for " + field, terms);
    s.sumTotalTermFreq = terms.getSumTotalTermFreq();
    s.sumDocFreq = terms.getSumDocFreq();
    TermsEnum te = terms.iterator();
    assertTrue("term not found: " + term, te.seekExact(new BytesRef(term)));
    s.docFreq = te.docFreq();
    s.totalTermFreq = te.totalTermFreq();
    PostingsEnum pe = te.postings(null, PostingsEnum.FREQS);
    assertEquals(0, pe.nextDoc());
    s.postingsFreq = pe.freq();
    NumericDocValues norms = leaf.getNormValues(field);
    if (norms != null && norms.advanceExact(0)) {
      s.norm = norms.longValue();
    }
    TopDocs td = searcher.search(q, 1);
    assertEquals("query must match the single document: " + q, 1, td.scoreDocs.length);
    s.score = td.scoreDocs[0].score;
    return s;
  }

  /** Index the fields with IndexWriter (default codec, BM25) and return the stats. */
  private static Stats indexWriterStats(
      Supplier<List<IndexableField>> doc, String field, String term, Query q) throws IOException {
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setSimilarity(new BM25Similarity());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc.get());
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        assertEquals(1, r.leaves().size());
        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setSimilarity(new BM25Similarity());
        return stats(r.leaves().get(0).reader(), searcher, field, term, q);
      }
    }
  }

  /** Index the same fields with MemoryIndex.fromDocument (BM25) and return the stats. */
  private static Stats memoryIndexStats(
      Supplier<List<IndexableField>> doc, String field, String term, Query q) throws IOException {
    MemoryIndex mi = MemoryIndex.fromDocument(doc.get(), new MockAnalyzer(random()));
    mi.setSimilarity(new BM25Similarity());
    IndexSearcher searcher = mi.createSearcher();
    searcher.setSimilarity(new BM25Similarity());
    LeafReader leaf = searcher.getIndexReader().leaves().get(0).reader();
    Stats s = stats(leaf, searcher, field, term, q);
    // MemoryIndex.search is the documented scoring entry point; it must agree with the searcher.
    assertEquals(s.score, mi.search(q), 0f);
    return s;
  }

  private static void assertSameStats(String what, Stats iw, Stats mi) {
    String msg = what + "\n  IndexWriter: " + iw + "\n  MemoryIndex: " + mi;
    assertEquals(msg + "\n  [docFreq]", iw.docFreq, mi.docFreq);
    assertEquals(msg + "\n  [postings freq]", iw.postingsFreq, mi.postingsFreq);
    assertEquals(msg + "\n  [totalTermFreq]", iw.totalTermFreq, mi.totalTermFreq);
    assertEquals(msg + "\n  [sumTotalTermFreq]", iw.sumTotalTermFreq, mi.sumTotalTermFreq);
    assertEquals(msg + "\n  [norm]", iw.norm, mi.norm);
    assertEquals(msg + "\n  [score]", iw.score, mi.score, 0f);
  }

  private static Supplier<List<IndexableField>> featureDoc(float value) {
    return () -> {
      List<IndexableField> doc = new ArrayList<>();
      doc.add(new FeatureField("features", "pagerank", value));
      return doc;
    };
  }

  /** FeatureField + newLinearQuery: score must be the feature value (10f is exactly encodable). */
  public void testFeatureFieldLinearQuery() throws IOException {
    Query q = FeatureField.newLinearQuery("features", "pagerank", 1f);
    Stats iw = indexWriterStats(featureDoc(10f), "features", "pagerank", q);
    // Oracle sanity: the FeatureField javadoc says the value is encoded as the term frequency,
    // and the linear function returns weight * value.
    assertEquals(Float.floatToIntBits(10f) >>> 15, iw.postingsFreq);
    assertEquals(10f, iw.score, 0f);
    Stats mi = memoryIndexStats(featureDoc(10f), "features", "pagerank", q);
    assertSameStats("FeatureField(features, pagerank, 10f) + newLinearQuery(w=1)", iw, mi);
  }

  /** FeatureField + newSaturationQuery with pivot == value: score must be 0.5 (class javadoc). */
  public void testFeatureFieldSaturationQuery() throws IOException {
    Query q = FeatureField.newSaturationQuery("features", "pagerank", 1f, 10f);
    Stats iw = indexWriterStats(featureDoc(10f), "features", "pagerank", q);
    assertEquals(0.5f, iw.score, 1e-6f);
    Stats mi = memoryIndexStats(featureDoc(10f), "features", "pagerank", q);
    assertSameStats("FeatureField(features, pagerank, 10f) + newSaturationQuery(w=1, pivot=10)", iw, mi);
  }

  /**
   * MemoryIndex.search javadoc: "0.0 indicating no match". The document matches the saturation
   * query, so search must return a positive score (IndexWriter: 0.5).
   */
  public void testSearchOfMatchingFeatureDocIsNotZero() throws IOException {
    Query q = FeatureField.newSaturationQuery("features", "pagerank", 1f, 10f);
    MemoryIndex mi = MemoryIndex.fromDocument(featureDoc(10f).get(), new MockAnalyzer(random()));
    assertEquals("the document matches the query", 1, mi.createSearcher().count(q));
    float score = mi.search(q);
    assertTrue(
        "MemoryIndex.search returned " + score + " (0.0 means 'no match') for a matching document",
        score > 0f);
  }

  private static FieldType customFreqType(IndexOptions options, boolean omitNorms) {
    FieldType ft = new FieldType();
    ft.setTokenized(true);
    ft.setStored(false);
    ft.setOmitNorms(omitNorms);
    ft.setIndexOptions(options);
    ft.freeze();
    return ft;
  }

  private static Supplier<List<IndexableField>> customFreqDoc(
      FieldType ft, String[] terms, int[] freqs) {
    return () -> {
      List<IndexableField> doc = new ArrayList<>();
      doc.add(new Field("f", new CustomFreqTokenStream(terms, freqs), ft));
      return doc;
    };
  }

  /** Control: frequency 1 everywhere (no custom value): both indexes must agree. */
  public void testControlFrequencyOneAgrees() throws IOException {
    FieldType ft = customFreqType(IndexOptions.DOCS_AND_FREQS, false);
    String[] terms = {"a", "b"};
    int[] freqs = {1, 1};
    Query q = new TermQuery(new Term("f", "a"));
    Stats iw = indexWriterStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    Stats mi = memoryIndexStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    assertSameStats("DOCS_AND_FREQS, freqs {a:1, b:1} (control)", iw, mi);
  }

  /** TermFrequencyAttribute on a DOCS_AND_FREQS field (the attribute's documented use). */
  public void testCustomFrequencyDocsAndFreqs() throws IOException {
    FieldType ft = customFreqType(IndexOptions.DOCS_AND_FREQS, false);
    String[] terms = {"a", "b"};
    int[] freqs = {7, 1};
    Query q = new TermQuery(new Term("f", "a"));
    Stats iw = indexWriterStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    // Oracle sanity (TermFrequencyAttribute javadoc: the custom frequency of the term in the doc).
    assertEquals(7, iw.postingsFreq);
    assertEquals(7, iw.totalTermFreq);
    assertEquals(8, iw.sumTotalTermFreq);
    Stats mi = memoryIndexStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    assertSameStats("DOCS_AND_FREQS, freqs {a:7, b:1}", iw, mi);
  }

  /** DOCS_AND_CUSTOM_FREQS (only on versions that have it; skipped elsewhere). */
  public void testCustomFrequencyDocsAndCustomFreqs() throws IOException {
    IndexOptions custom;
    try {
      custom = IndexOptions.valueOf("DOCS_AND_CUSTOM_FREQS");
    } catch (IllegalArgumentException e) {
      assumeTrue("IndexOptions.DOCS_AND_CUSTOM_FREQS does not exist in this version", false);
      return;
    }
    FieldType ft = customFreqType(custom, false);
    String[] terms = {"a", "b"};
    int[] freqs = {7, 1};
    Query q = new TermQuery(new Term("f", "a"));
    Stats iw = indexWriterStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    assertEquals(7, iw.postingsFreq);
    Stats mi = memoryIndexStats(customFreqDoc(ft, terms, freqs), "f", "a", q);
    assertSameStats("DOCS_AND_CUSTOM_FREQS, freqs {a:7, b:1}", iw, mi);
  }
}
