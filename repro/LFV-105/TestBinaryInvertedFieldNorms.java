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
package org.apache.lucene.index;

import java.io.IOException;
import java.util.Arrays;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * An untokenized value inverted through the single-term path (InvertableType.BINARY) must count as
 * one term, as it does through a TokenStream: same field length, same norm, same BM25 score.
 */
public class TestBinaryInvertedFieldNorms extends LuceneTestCase {

  /** A plain Field that asks the indexing chain for the single-term (BINARY) inversion path. */
  private static final class BinaryInvertedField extends Field {
    BinaryInvertedField(String name, String value, FieldType type) {
      super(name, new BytesRef(value), type);
    }

    @Override
    public InvertableType invertableType() {
      return InvertableType.BINARY;
    }
  }

  private static FieldType untokenized(IndexOptions indexOptions) {
    FieldType ft = new FieldType();
    ft.setTokenized(false);
    ft.setIndexOptions(indexOptions);
    ft.setOmitNorms(false); // the FieldType default
    ft.freeze();
    return ft;
  }

  /** Doc 0 indexes the values through a plain Field (TOKEN_STREAM), doc 1 as BINARY. */
  private void assertSameNormAndScore(IndexOptions indexOptions, String... values)
      throws IOException {
    FieldType ft = untokenized(indexOptions);
    try (Directory dir = newDirectory()) {
      // default IndexWriterConfig: BM25 similarity
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document tokenStreamDoc = new Document();
        Document binaryDoc = new Document();
        for (String value : values) {
          tokenStreamDoc.add(new Field("tag", new BytesRef(value), ft));
          binaryDoc.add(new BinaryInvertedField("tag", value, ft));
        }
        w.addDocument(tokenStreamDoc); // doc 0
        w.addDocument(binaryDoc); // doc 1
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        NumericDocValues norms = getOnlyLeafReader(reader).getNormValues("tag");
        long[] norm = new long[2];
        for (int doc = 0; doc < 2; doc++) {
          assertTrue(norms.advanceExact(doc));
          norm[doc] = norms.longValue();
        }
        float[] score = new float[2];
        TermQuery query = new TermQuery(new Term("tag", values[0]));
        for (ScoreDoc hit : new IndexSearcher(reader).search(query, 2).scoreDocs) {
          score[hit.doc] = hit.score;
        }
        String observed =
            indexOptions
                + ", "
                + values.length
                + " value(s): norms [TOKEN_STREAM, BINARY] = "
                + Arrays.toString(norm)
                + ", BM25 scores = "
                + Arrays.toString(score);
        assertEquals(observed, norm[0], norm[1]);
        assertEquals(observed, score[0], score[1], 0f);
      }
    }
  }

  public void testOneValue() throws IOException {
    assertSameNormAndScore(IndexOptions.DOCS_AND_FREQS, "x");
  }

  public void testTwoValues() throws IOException {
    assertSameNormAndScore(IndexOptions.DOCS_AND_FREQS, "x", "y");
  }

  /** Control: for DOCS fields the norm counts unique terms, not the field length. Passes today. */
  public void testDocsOnlyControl() throws IOException {
    assertSameNormAndScore(IndexOptions.DOCS, "x");
  }
}
