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
package org.apache.lucene.queries.payloads;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestNestedPayloadScoreQuery extends LuceneTestCase {

  public void testNestedPayloadScore() throws Exception {
    Token x = new Token("x", 0, 1);
    x.setPayload(new BytesRef(new byte[] {3}));
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new TextField("f", new CannedTokenStream(x)));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        PayloadScoreQuery inner =
            new PayloadScoreQuery(
                new SpanTermQuery(new Term("f", "x")),
                new MaxPayloadFunction(),
                PayloadDecoder.FLOAT_DECODER,
                false);
        PayloadScoreQuery outer =
            new PayloadScoreQuery(
                inner, new MaxPayloadFunction(), PayloadDecoder.FLOAT_DECODER, false);
        float score = new IndexSearcher(reader).search(outer, 1).scoreDocs[0].score;
        // expected: payload 3 (the inner spans carry it); actual: 1, the no-payload score
        assertEquals("score of the nested PayloadScoreQuery", 3f, score, 0f);
      }
    }
  }
}
