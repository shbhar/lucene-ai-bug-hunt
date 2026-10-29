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
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FieldInvertState;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTermDocFieldStatsVsFieldLength extends LuceneTestCase {

  public void testSumTotalTermFreqIsSumOfFieldLengths() throws IOException {
    long[] sumOfFieldLengths = new long[1];
    IndexWriterConfig iwc = new IndexWriterConfig();
    iwc.setSimilarity(
        new BM25Similarity() {
          @Override
          public long computeNorm(FieldInvertState state) {
            sumOfFieldLengths[0] += state.getLength();
            return super.computeNorm(state);
          }
        });
    FieldType ft = new FieldType(TextField.TYPE_NOT_STORED);
    ft.setIndexOptions(IndexOptions.DOCS_AND_CUSTOM_FREQS);
    Token a = new Token("a", 0, 1);
    a.setTermFrequency(5);
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      Document doc = new Document();
      doc.add(new Field("f", new CannedTokenStream(a), ft));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        // expected 1 (FieldStats: "also the sum of each document's field length"); actual 5
        assertEquals(
            "sumTotalTermFreq",
            sumOfFieldLengths[0],
            new IndexSearcher(r).fieldStats("f").sumTotalTermFreq());
      }
    }
  }
}
