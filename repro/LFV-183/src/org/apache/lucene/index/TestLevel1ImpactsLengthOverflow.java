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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.TermFrequencyAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;
import org.apache.lucene.util.BytesRef;

/**
 * The postings writer stores the byte length of the level-1 competitive impacts (one level-1
 * block = 32 x 256 = 8192 docs) in a signed short. With norms that do not fit in a byte, every
 * document of a level-1 block can contribute one competitive (freq, norm) pair, and the encoded
 * list can exceed Short.MAX_VALUE bytes. Both documents below are legal: custom term frequencies
 * (TermFrequencyAttribute) on a DOCS_AND_FREQS field, and a Similarity whose norm is the field
 * length.
 */
public class TestLevel1ImpactsLengthOverflow extends LuceneTestCase {

  /** Emits the single token "t" with a custom term frequency. */
  private static final class OneTokenWithFreq extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final TermFrequencyAttribute termFreq = addAttribute(TermFrequencyAttribute.class);
    private final int freq;
    private boolean done;

    OneTokenWithFreq(int freq) {
      this.freq = freq;
    }

    @Override
    public boolean incrementToken() {
      if (done) {
        return false;
      }
      clearAttributes();
      term.append("t");
      termFreq.setTermFrequency(freq);
      done = true;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      done = false;
    }
  }

  /** Norm = field length, shifted left by {@code shift} bits. Only used at index time here. */
  private static final class LengthNormSimilarity extends BM25Similarity {
    private final int shift;

    LengthNormSimilarity(int shift) {
      this.shift = shift;
    }

    @Override
    public long computeNorm(FieldInvertState state) {
      return ((long) state.getLength()) << shift;
    }
  }

  private void indexAndReadBack(int normShift) throws Exception {
    final int numDocs = 8192; // exactly one level-1 skip block
    final int[] freqs = new int[numDocs];
    for (int i = 0; i < numDocs; i++) {
      freqs[i] = 1 + i * 8193; // max 67,108,864
    }
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig();
      iwc.setCodec(TestUtil.getDefaultCodec());
      iwc.setSimilarity(new LengthNormSimilarity(normShift));
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      iwc.setMaxBufferedDocs(numDocs + 1);
      iwc.setRAMBufferSizeMB(256);
      FieldType ft = new FieldType();
      ft.setTokenized(true);
      ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS); // required by TermFrequencyAttribute
      ft.freeze();
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < numDocs; i++) {
          Document doc = new Document();
          doc.add(new Field("f", new OneTokenWithFreq(freqs[i]), ft));
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        TermsEnum termsEnum = reader.leaves().get(0).reader().terms("f").iterator();
        assertTrue(termsEnum.seekExact(new BytesRef("t")));
        assertEquals(numDocs, termsEnum.docFreq());
        PostingsEnum postings = termsEnum.postings(null, PostingsEnum.FREQS);
        for (int i = 0; i < numDocs; i++) {
          assertEquals(i, postings.nextDoc());
          assertEquals("freq of doc " + i, freqs[i], postings.freq());
        }
        assertEquals(DocIdSetIterator.NO_MORE_DOCS, postings.nextDoc());
      }
    }
  }

  /** Norm = exact field length (as in Lucene's own TestCustomNorms). */
  public void testExactLengthNorms() throws Exception {
    indexAndReadBack(0);
  }

  /** Norm = field length << 24: wider norm deltas. */
  public void testWideNorms() throws Exception {
    indexAndReadBack(24);
  }
}
