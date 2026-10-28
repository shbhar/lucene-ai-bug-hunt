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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * A field whose TokenStream sets {@link TermFrequencyAttribute} (FeatureField does this to store
 * its value) must get the same postings, statistics, norms and scores in a MemoryIndex as in a
 * one-document index written by IndexWriter. Each test first checks the IndexWriter value.
 */
public class TestMemoryIndexCustomTermFrequencies extends LuceneTestCase {

  /** Returns "freq=.. totalTermFreq=.. sumTotalTermFreq=.. norm=.. score=.." for term f:term. */
  private static String stats(LeafReader leaf, String field, String term, Query query)
      throws IOException {
    Terms terms = leaf.terms(field);
    TermsEnum termsEnum = terms.iterator();
    assertTrue(termsEnum.seekExact(new BytesRef(term)));
    PostingsEnum postings = termsEnum.postings(null, PostingsEnum.FREQS);
    assertEquals(0, postings.nextDoc());
    NumericDocValues norms = leaf.getNormValues(field);
    String norm =
        norms != null && norms.advanceExact(0) ? Long.toString(norms.longValue()) : "none";
    TopDocs topDocs = new IndexSearcher(leaf).search(query, 1);
    assertEquals("the document matches " + query, 1, topDocs.scoreDocs.length);
    return "freq="
        + postings.freq()
        + " totalTermFreq="
        + termsEnum.totalTermFreq()
        + " sumTotalTermFreq="
        + terms.getSumTotalTermFreq()
        + " norm="
        + norm
        + " score="
        + topDocs.scoreDocs[0].score;
  }

  /** The same document, indexed by IndexWriter (default codec and similarity). */
  private String indexWriterStats(List<IndexableField> doc, String field, String term, Query query)
      throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc =
          new IndexWriterConfig(new StandardAnalyzer()).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter writer = new IndexWriter(dir, iwc)) {
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        return stats(reader.leaves().get(0).reader(), field, term, query);
      }
    }
  }

  /** The same document, indexed by MemoryIndex.fromDocument. */
  private static String memoryIndexStats(
      List<IndexableField> doc, String field, String term, Query query) throws IOException {
    MemoryIndex memoryIndex = MemoryIndex.fromDocument(doc, new StandardAnalyzer());
    LeafReader leaf = (LeafReader) memoryIndex.createSearcher().getIndexReader();
    return stats(leaf, field, term, query);
  }

  public void testFeatureFieldLinearScore() throws IOException {
    List<IndexableField> doc = List.of(new FeatureField("features", "pagerank", 10f));
    Query query = FeatureField.newLinearQuery("features", "pagerank", 1f);
    String expected = indexWriterStats(doc, "features", "pagerank", query);
    // FeatureField stores 10f as the term frequency floatToIntBits(10f) >>> 15 = 33344
    assertEquals(
        "freq=33344 totalTermFreq=33344 sumTotalTermFreq=33344 norm=none score=10.0", expected);
    assertEquals(expected, memoryIndexStats(doc, "features", "pagerank", query));
  }

  public void testFeatureFieldSaturationMatchScoresZero() throws IOException {
    List<IndexableField> doc = List.of(new FeatureField("features", "pagerank", 10f));
    Query query = FeatureField.newSaturationQuery("features", "pagerank", 1f, 10f);
    String expected = indexWriterStats(doc, "features", "pagerank", query);
    assertTrue(expected, expected.endsWith(" score=0.5")); // S / (S + pivot) with S = pivot
    MemoryIndex memoryIndex = MemoryIndex.fromDocument(doc, new StandardAnalyzer());
    assertEquals(1, memoryIndex.createSearcher().count(query)); // the document matches ...
    // ... but MemoryIndex.search documents 0.0 as "no match"
    assertEquals("MemoryIndex.search", 0.5f, memoryIndex.search(query), 0f);
  }

  /** Emits terms a and b once each, with custom frequencies. */
  private static final class CustomFreqTokenStream extends TokenStream {
    private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute freqAtt = addAttribute(TermFrequencyAttribute.class);
    private final int freqA, freqB;
    private int upto;

    CustomFreqTokenStream(int freqA, int freqB) {
      this.freqA = freqA;
      this.freqB = freqB;
    }

    @Override
    public boolean incrementToken() {
      if (upto == 2) {
        return false;
      }
      clearAttributes();
      termAtt.append(upto == 0 ? "a" : "b");
      freqAtt.setTermFrequency(upto == 0 ? freqA : freqB);
      upto++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
    }
  }

  private static List<IndexableField> customFreqDoc(IndexOptions options, int freqA, int freqB) {
    FieldType type = new FieldType();
    type.setTokenized(true);
    type.setIndexOptions(options);
    type.freeze();
    return List.of(new Field("f", new CustomFreqTokenStream(freqA, freqB), type));
  }

  private void checkCustomFrequency(IndexOptions options, String expectedPrefix)
      throws IOException {
    Query query = new TermQuery(new Term("f", "a"));
    String expected = indexWriterStats(customFreqDoc(options, 7, 1), "f", "a", query);
    assertTrue(expected, expected.startsWith(expectedPrefix));
    assertEquals(
        options.toString(),
        expected,
        memoryIndexStats(customFreqDoc(options, 7, 1), "f", "a", query));
  }

  public void testCustomFrequencyDocsAndFreqs() throws IOException {
    checkCustomFrequency(
        IndexOptions.DOCS_AND_FREQS, "freq=7 totalTermFreq=7 sumTotalTermFreq=8 norm=8 ");
  }

  public void testCustomFrequencyDocsAndCustomFreqs() throws IOException { // since 10.5.0
    checkCustomFrequency(
        IndexOptions.DOCS_AND_CUSTOM_FREQS, "freq=7 totalTermFreq=7 sumTotalTermFreq=8 norm=2 ");
  }

  /** Control: with frequency 1 everywhere the two indexes agree. */
  public void testControlFrequencyOne() throws IOException {
    Query query = new TermQuery(new Term("f", "a"));
    List<IndexableField> doc = customFreqDoc(IndexOptions.DOCS_AND_FREQS, 1, 1);
    String expected = indexWriterStats(doc, "f", "a", query);
    doc = customFreqDoc(IndexOptions.DOCS_AND_FREQS, 1, 1);
    assertEquals(expected, memoryIndexStats(doc, "f", "a", query));
  }
}
