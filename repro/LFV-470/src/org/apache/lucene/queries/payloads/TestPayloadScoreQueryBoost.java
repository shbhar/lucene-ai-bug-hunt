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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.BoostQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.CannedTokenStream;
import org.apache.lucene.tests.analysis.Token;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * BoostQuery: "wrap query in such a way that the produced scores will be boosted by boost". A
 * PayloadScoreQuery built with includeSpanScore=false ignores the boost. Public API only.
 */
public class TestPayloadScoreQueryBoost extends LuceneTestCase {

  private static Document doc(String term, byte payload) {
    Token t = new Token(term, 0, term.length());
    t.setPayload(new BytesRef(new byte[] {payload}));
    Document d = new Document();
    d.add(new Field("body", new CannedTokenStream(t), TextField.TYPE_NOT_STORED));
    return d;
  }

  private static Query payloadQuery(boolean includeSpanScore) {
    return new PayloadScoreQuery(
        new SpanTermQuery(new Term("body", "x")),
        new MaxPayloadFunction(),
        PayloadDecoder.FLOAT_DECODER,
        includeSpanScore);
  }

  public void testBoostMultipliesScore() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("x", (byte) 4));
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        for (boolean includeSpanScore : new boolean[] {true, false}) {
          Query q = payloadQuery(includeSpanScore);
          float unboosted = searcher.search(q, 1).scoreDocs[0].score;
          float boosted = searcher.search(new BoostQuery(q, 3f), 1).scoreDocs[0].score;
          assertEquals(
              "includeSpanScore=" + includeSpanScore + ": unboosted " + unboosted,
              3f * unboosted,
              boosted,
              1e-5f * boosted);
        }
      }
    }
  }

  /** A clause boost cannot change the ranking: doc 0 (payload 4) should win with boost 10. */
  public void testClauseBoostChangesRanking() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(doc("x", (byte) 1)); // payload query scores 1
        w.addDocument(doc("y", (byte) 1));
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query y = new BoostQuery(new TermQuery(new Term("body", "y")), 5f); // BM25 ~ 5 * 0.29
        Query q =
            new BooleanQuery.Builder()
                .add(new BoostQuery(payloadQuery(false), 10f), BooleanClause.Occur.SHOULD)
                .add(y, BooleanClause.Occur.SHOULD)
                .build();
        TopDocs td = searcher.search(q, 2);
        assertEquals("score of the boosted payload hit", 10f, td.scoreDocs[td.scoreDocs[0].doc == 0 ? 0 : 1].score, 1e-4f);
        assertEquals(0, td.scoreDocs[0].doc);
      }
    }
  }
}
