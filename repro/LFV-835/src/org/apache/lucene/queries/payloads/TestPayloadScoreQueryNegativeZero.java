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

import java.util.Arrays;
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestPayloadScoreQueryNegativeZero extends LuceneTestCase {

  public void testTopOneIsHeadOfTopTwo() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      for (float payload : new float[] {-0f, 0f}) {
        Token token = new Token("x", 0, 1);
        token.setPayload(new BytesRef(PayloadHelper.encodeFloat(payload)));
        Document doc = new Document();
        doc.add(new TextField("f", new CannedTokenStream(token)));
        writer.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        PayloadScoreQuery query =
            new PayloadScoreQuery(
                new SpanTermQuery(new Term("f", "x")),
                new MaxPayloadFunction(),
                bytes -> PayloadHelper.decodeFloat(bytes.bytes, bytes.offset),
                false);
        ScoreDoc[] top1 = searcher.search(query, 1).scoreDocs;
        ScoreDoc[] top2 = searcher.search(query, 2).scoreDocs;
        // expected: both docs score 0, so top-1 is the head of top-2; actual: doc 0 scores -0.0
        assertEquals("top-2: " + Arrays.toString(top2), top2[0].doc, top1[0].doc);
      }
    }
  }
}
