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
package org.apache.lucene.search;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.miscellaneous.DelimitedTermFrequencyTokenFilter;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.index.Terms;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.SmallFloat;

/**
 * IndexOptions.DOCS_AND_CUSTOM_FREQS makes IndexWriter count a field's length as its number of
 * terms, but the collection statistics (Terms#getSumTotalTermFreq, hence FieldStats#sumTotalTermFreq
 * and BM25's avgdl) still sum the custom frequencies. FieldStats documents sumTotalTermFreq as "also
 * the sum of each document's field length across all documents", and FieldInfo#isTermDocField says
 * the term frequency "for statistical and scoring purposes is treated as 1".
 */
public class TestTermDocFieldLengthVsStats extends LuceneTestCase {

  // "term|weight" tokens, the documented way to feed custom term frequencies
  private static final Analyzer ANALYZER =
      new Analyzer() {
        @Override
        protected TokenStreamComponents createComponents(String fieldName) {
          Tokenizer t = new WhitespaceTokenizer();
          return new TokenStreamComponents(t, new DelimitedTermFrequencyTokenFilter(t));
        }
      };

  /** doc 0: two terms, doc 1: four terms, every custom frequency 5. */
  private static final String[] DOCS = {"a|5 b|5", "a|5 b|5 c|5 d|5"};

  private static Directory index(IndexOptions io) throws IOException {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED); // norms on, as in TextField
    ft.setIndexOptions(io);
    ft.freeze();
    Directory dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(ANALYZER))) {
      for (String s : DOCS) {
        Document doc = new Document();
        doc.add(new Field("f", s, ft));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    return dir;
  }

  /** Sum of the per-document field lengths (decoded norms; exact for these small lengths). */
  private static long sumOfFieldLengths(LeafReader leaf) throws IOException {
    NumericDocValues norms = leaf.getNormValues("f");
    long sum = 0;
    for (int d = norms.nextDoc(); d != NumericDocValues.NO_MORE_DOCS; d = norms.nextDoc()) {
      sum += SmallFloat.byte4ToInt((byte) norms.longValue());
    }
    return sum;
  }

  private static void checkLengthStats(IndexOptions io) throws IOException {
    try (Directory dir = index(io);
        DirectoryReader r = DirectoryReader.open(dir)) {
      LeafReader leaf = getOnlyLeafReader(r);
      Terms terms = leaf.terms("f");
      assertEquals(
          io + ": FieldStats#sumTotalTermFreq is 'also the sum of each document's field length'",
          sumOfFieldLengths(leaf),
          terms.getSumTotalTermFreq());
    }
  }

  /** BM25 explains dl (from the norm) and avgdl (from sumTotalTermFreq / docCount). */
  private static void checkBm25Lengths(IndexOptions io) throws IOException {
    try (Directory dir = index(io);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(r);
      searcher.setSimilarity(new org.apache.lucene.search.similarities.BM25Similarity());
      Query q = new TermQuery(new Term("f", "a"));
      double sumDl = 0;
      double avgdl = -1;
      for (int doc = 0; doc < r.maxDoc(); doc++) {
        Explanation e = searcher.explain(q, doc);
        sumDl += detail(e, "dl, length of field");
        avgdl = detail(e, "avgdl, average length of field");
      }
      assertEquals(
          io + ": BM25 avgdl should be the mean of the dl values it is compared with",
          sumDl / r.maxDoc(),
          avgdl,
          1e-6);
    }
  }

  private static double detail(Explanation e, String description) {
    if (e.getDescription().equals(description)) {
      return e.getValue().doubleValue();
    }
    for (Explanation d : e.getDetails()) {
      double v = detail(d, description);
      if (v >= 0) {
        return v;
      }
    }
    return -1;
  }

  // controls: the same custom frequencies on DOCS_AND_FREQS (length = sum of freqs) are consistent
  public void testLegacyCustomFreqsLengthStats() throws IOException {
    checkLengthStats(IndexOptions.DOCS_AND_FREQS);
  }

  public void testLegacyCustomFreqsBm25Lengths() throws IOException {
    checkBm25Lengths(IndexOptions.DOCS_AND_FREQS);
  }

  // DOCS_AND_CUSTOM_FREQS (termdoc): length counts terms (2 and 4) but sumTotalTermFreq is 30
  public void testTermDocLengthStats() throws IOException {
    checkLengthStats(IndexOptions.DOCS_AND_CUSTOM_FREQS);
  }

  public void testTermDocBm25Lengths() throws IOException {
    checkBm25Lengths(IndexOptions.DOCS_AND_CUSTOM_FREQS);
  }
}
