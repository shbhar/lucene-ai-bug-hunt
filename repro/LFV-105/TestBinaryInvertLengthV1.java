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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.InvertableType;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * addDocument only, public API only. Two documents hold the same untokenized values with the same
 * FieldType; the only difference is the field's invertableType(): doc 0 uses a plain Field
 * (TOKEN_STREAM, one token per value), doc 1 uses a Field subclass that returns BINARY. Both must
 * produce the same FieldInvertState length, norm and BM25 score.
 */
public class TestBinaryInvertLengthV1 extends LuceneTestCase {

  /** Identical to Field except that it asks IndexingChain for the single-term (BINARY) path. */
  static final class BinaryInvertedField extends Field {
    BinaryInvertedField(String name, BytesRef value, IndexableFieldType type) {
      super(name, value, type);
    }

    @Override
    public InvertableType invertableType() {
      return InvertableType.BINARY;
    }
  }

  /** BM25 that records FieldInvertState.getLength() for every computed norm. */
  static final class LengthRecordingSimilarity extends BM25Similarity {
    final List<Integer> lengths = Collections.synchronizedList(new ArrayList<>());

    @Override
    public long computeNorm(FieldInvertState state) {
      lengths.add(state.getLength());
      return super.computeNorm(state);
    }
  }

  private static FieldType untokenizedWithNorms(IndexOptions opts) {
    FieldType ft = new FieldType();
    ft.setTokenized(false);
    ft.setIndexOptions(opts);
    ft.setOmitNorms(false);
    ft.freeze();
    return ft;
  }

  /** Indexes doc 0 (TOKEN_STREAM) and doc 1 (BINARY) with the same values and compares them. */
  private void check(IndexOptions opts, boolean assertLength, String... values) throws Exception {
    FieldType ft = untokenizedWithNorms(opts);
    LengthRecordingSimilarity sim = new LengthRecordingSimilarity();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig().setSimilarity(sim))) {
        Document tokenStreamDoc = new Document();
        for (String v : values) {
          tokenStreamDoc.add(new Field("f", new BytesRef(v), ft));
        }
        w.addDocument(tokenStreamDoc); // doc 0
        Document binaryDoc = new Document();
        for (String v : values) {
          binaryDoc.add(new BinaryInvertedField("f", new BytesRef(v), ft));
        }
        w.addDocument(binaryDoc); // doc 1
      }
      List<Integer> lengths = new ArrayList<>(sim.lengths);
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        NumericDocValues norms = MultiDocValues.getNormValues(r, "f");
        long norm0 = norms.advanceExact(0) ? norms.longValue() : -1;
        long norm1 = norms.advanceExact(1) ? norms.longValue() : -1;

        IndexSearcher searcher = new IndexSearcher(r);
        searcher.setSimilarity(new BM25Similarity());
        TopDocs td = searcher.search(new TermQuery(new Term("f", values[0])), 10);
        float[] score = new float[2];
        for (ScoreDoc sd : td.scoreDocs) {
          score[sd.doc] = sd.score;
        }

        String obs =
            "indexOptions="
                + opts
                + " valuesPerDoc="
                + values.length
                + " getLength() [doc0 TOKEN_STREAM, doc1 BINARY]="
                + lengths
                + " norms=["
                + norm0
                + ", "
                + norm1
                + "] BM25 scores=["
                + score[0]
                + ", "
                + score[1]
                + "]";
        System.out.println("OBSERVED " + getTestName() + ": " + obs);

        assertEquals("expected one computeNorm call per doc: " + obs, 2, lengths.size());
        assertEquals(
            "sanity: the TOKEN_STREAM path counts one term per untokenized value: " + obs,
            values.length,
            (int) lengths.get(0));
        if (assertLength) {
          assertEquals(
              "FieldInvertState.getLength() ('total number of terms in this field') must not"
                  + " depend on invertableType(): "
                  + obs,
              lengths.get(0),
              lengths.get(1));
        }
        assertEquals("norm must not depend on invertableType(): " + obs, norm0, norm1);
        assertEquals(
            "same term, same freq, same field => same BM25 score: " + obs, score[0], score[1], 0f);
      }
    }
  }

  /** Expected to FAIL on affected versions: norm 2 instead of 1. */
  public void testSingleValueDocsAndFreqs() throws Exception {
    check(IndexOptions.DOCS_AND_FREQS, true, "a");
  }

  /** Expected to FAIL on affected versions: norm 4 instead of 2 (2k instead of k). */
  public void testTwoValuesDocsAndFreqs() throws Exception {
    check(IndexOptions.DOCS_AND_FREQS, true, "a", "b");
  }

  /**
   * Control, expected to PASS: for DOCS-only fields the default norm uses the unique-term count, not
   * getLength(), so the norm and score are unaffected (the printed getLength() still shows the
   * difference).
   */
  public void testDocsOnlyControlNormUnaffected() throws Exception {
    check(IndexOptions.DOCS, false, "a");
  }
}
