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
package org.apache.lucene.codecs.lucene104;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.search.similarities.Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestLevel1ImpactsLength extends LuceneTestCase {

  /** norm = field length, as in TestCustomNorms; scores never rise with the (unsigned) norm. */
  private static class LengthNormSimilarity extends Similarity {
    @Override
    public long computeNorm(FieldInvertState state) {
      return state.getLength();
    }

    @Override
    public SimScorer scorer(float boost, FieldStats fieldStats, TermStats... termStats) {
      return new SimScorer() {
        @Override
        public float score(float freq, long norm) {
          double length = norm >= 0 ? norm : (norm >>> 1) * 2.0;
          return (float) (boost * freq / (freq + length));
        }
      };
    }
  }

  public void testLevel1ImpactsRoundTrip() throws Exception {
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_FREQS); // custom term frequencies need no positions
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setCodec(TestUtil.getDefaultCodec());
    iwc.setSimilarity(new LengthNormSimilarity());
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < 8192; i++) { // exactly one level-1 block (32 x 256 docs)
        Token token = new Token("t", 0, 1);
        token.setTermFrequency(1 + 8193 * i); // a single token, so norm = field length = freq
        Document doc = new Document();
        doc.add(new Field("f", new CannedTokenStream(token), ft));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        PostingsEnum postings =
            getOnlyLeafReader(reader).postings(new Term("f", "t"), PostingsEnum.FREQS);
        for (int i = 0; i < 8192; i++) {
          assertEquals(i, postings.nextDoc());
          assertEquals(1 + 8193 * i, postings.freq());
        }
      }
    }
  }
}
