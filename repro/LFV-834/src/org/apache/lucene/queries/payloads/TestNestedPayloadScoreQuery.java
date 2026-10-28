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
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.FieldMaskingSpanQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * A PayloadScoreQuery is a SpanQuery, and its spans are the spans of the query it wraps. Wrapping
 * it in a second PayloadScoreQuery should therefore score with the outer payload function over
 * those spans' payloads. Instead the outer query takes its SimScorer from the inner weight, which
 * is a PayloadSpanWeight built without term statistics, so the SimScorer is null: SpanScorer then
 * skips collecting payloads (every hit scores 1.0), or throws when the span score is needed.
 */
public class TestNestedPayloadScoreQuery extends LuceneTestCase {

  /** One token "x" with a one-byte payload (the layout PayloadDecoder.FLOAT_DECODER reads). */
  private static final class OneToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PayloadAttribute payload = addAttribute(PayloadAttribute.class);
    private final byte value;
    private boolean done;

    OneToken(byte value) {
      this.value = value;
    }

    @Override
    public boolean incrementToken() {
      if (done) {
        return false;
      }
      clearAttributes();
      term.append("x");
      payload.setPayload(new BytesRef(new byte[] {value}));
      done = true;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      done = false;
    }
  }

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (byte v : new byte[] {3, 5}) { // doc 0: payload 3, doc 1: payload 5
        Document doc = new Document();
        doc.add(new TextField("f", new OneToken(v)));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private static SpanQuery term() {
    return new SpanTermQuery(new Term("f", "x"));
  }

  private static PayloadScoreQuery inner() {
    return new PayloadScoreQuery(
        term(), new SumPayloadFunction(), PayloadDecoder.FLOAT_DECODER, false);
  }

  private void assertScores(SpanQuery q, float doc0, float doc1) throws IOException {
    TopDocs td = searcher.search(q, 10);
    assertEquals(2, td.scoreDocs.length);
    float[] scores = new float[2];
    for (ScoreDoc sd : td.scoreDocs) {
      scores[sd.doc] = sd.score;
    }
    assertEquals("doc 0", doc0, scores[0], 0f);
    assertEquals("doc 1", doc1, scores[1], 0f);
  }

  /** Control: the outer function over the plain span query gives the payload values. */
  public void testControlNotNested() throws IOException {
    assertScores(
        new PayloadScoreQuery(term(), new MaxPayloadFunction(), PayloadDecoder.FLOAT_DECODER, false),
        3f,
        5f);
  }

  /** The nested query has the same spans and payloads, so it must give the same scores. */
  public void testNestedPayloadOnly() throws IOException {
    assertScores(
        new PayloadScoreQuery(inner(), new MaxPayloadFunction(), PayloadDecoder.FLOAT_DECODER, false),
        3f,
        5f);
  }

  /** Same through FieldMaskingSpanQuery, whose weight is the masked query's weight. */
  public void testNestedThroughFieldMaskingPayloadOnly() throws IOException {
    assertScores(
        new PayloadScoreQuery(
            new FieldMaskingSpanQuery(inner(), "f"),
            new MaxPayloadFunction(),
            PayloadDecoder.FLOAT_DECODER,
            false),
        3f,
        5f);
  }

  /** The 3-argument constructor (includeSpanScore = true): searching must not throw. */
  public void testNestedWithSpanScore() throws IOException {
    PayloadScoreQuery q =
        new PayloadScoreQuery(inner(), new MaxPayloadFunction(), PayloadDecoder.FLOAT_DECODER);
    TopDocs td = searcher.search(q, 10);
    assertEquals(2, td.scoreDocs.length);
    // doc 1 (payload 5) must outrank doc 0 (payload 3): same term statistics and norms
    assertEquals(1, td.scoreDocs[0].doc);
  }
}
