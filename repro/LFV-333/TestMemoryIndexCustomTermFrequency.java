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
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
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
 * TermFrequencyAttribute lets a TokenStream supply a custom term frequency (DOCS_AND_FREQS fields,
 * and the DOCS_AND_CUSTOM_FREQS option); FeatureField stores its feature value this way.
 * IndexWriter indexes the custom frequency; MemoryIndex ignores the attribute and counts every
 * token as frequency 1, so postings, statistics, norms and scores differ from the one-document
 * IndexWriter index that MemoryIndex is meant to stand in for.
 */
public class TestMemoryIndexCustomTermFrequency extends LuceneTestCase {

  private float scoreOnIndexWriter(List<IndexableField> doc, Analyzer analyzer, Query q)
      throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        TopDocs td = new IndexSearcher(r).search(q, 1);
        assertEquals(1, td.scoreDocs.length);
        return td.scoreDocs[0].score;
      }
    }
  }

  public void testFeatureFieldScore() throws IOException {
    Analyzer analyzer = new StandardAnalyzer();
    Query q = FeatureField.newLinearQuery("features", "pagerank", 1f);
    float expected = scoreOnIndexWriter(List.of(new FeatureField("features", "pagerank", 10f)), analyzer, q);
    assertEquals(10f, expected, 0.1f); // linear function returns the (16-bit precision) feature value

    MemoryIndex mi = MemoryIndex.fromDocument(List.of(new FeatureField("features", "pagerank", 10f)), analyzer);
    assertEquals("FeatureField linear score", expected, mi.search(q), 0f);
  }

  /** One token "a" whose TokenStream sets a custom term frequency of 7. */
  private static TokenStream oneTokenWithFreq(int freq) {
    return new TokenStream() {
      final CharTermAttribute term = addAttribute(CharTermAttribute.class);
      final TermFrequencyAttribute tf = addAttribute(TermFrequencyAttribute.class);
      boolean done;

      @Override
      public boolean incrementToken() {
        if (done) return false;
        clearAttributes();
        term.append("a");
        tf.setTermFrequency(freq);
        done = true;
        return true;
      }

      @Override
      public void reset() throws IOException {
        super.reset();
        done = false;
      }
    };
  }

  private void checkCustomFrequency(IndexOptions options) throws IOException {
    FieldType ft = new FieldType();
    ft.setTokenized(true);
    ft.setIndexOptions(options);
    ft.freeze();
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer).setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(List.of(new Field("f", oneTokenWithFreq(7), ft)));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        LeafReader iw = r.leaves().get(0).reader();
        MemoryIndex memoryIndex = new MemoryIndex();
        memoryIndex.addField(new Field("f", oneTokenWithFreq(7), ft), analyzer);
        LeafReader mi = (LeafReader) memoryIndex.createSearcher().getIndexReader();

        assertEquals(options + " sumTotalTermFreq", iw.terms("f").getSumTotalTermFreq(), mi.terms("f").getSumTotalTermFreq());
        TermsEnum a = iw.terms("f").iterator(), b = mi.terms("f").iterator();
        assertTrue(a.seekExact(new BytesRef("a")));
        assertTrue(b.seekExact(new BytesRef("a")));
        PostingsEnum pa = a.postings(null, PostingsEnum.FREQS), pb = b.postings(null, PostingsEnum.FREQS);
        assertEquals(0, pa.nextDoc());
        assertEquals(0, pb.nextDoc());
        assertEquals(7, pa.freq());
        assertEquals(options + " postings freq", pa.freq(), pb.freq());
        TermQuery q = new TermQuery(new Term("f", "a"));
        assertEquals(options + " TermQuery score", new IndexSearcher(iw).search(q, 1).scoreDocs[0].score, memoryIndex.search(q), 0f);
      }
    }
  }

  public void testCustomTermFrequencyDocsAndFreqs() throws IOException {
    checkCustomFrequency(IndexOptions.DOCS_AND_FREQS);
  }
}
