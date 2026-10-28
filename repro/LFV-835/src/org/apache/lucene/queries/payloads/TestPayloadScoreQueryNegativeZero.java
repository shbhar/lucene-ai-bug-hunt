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
import org.apache.lucene.analysis.payloads.PayloadHelper;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * PayloadScoreQuery maps negative payload scores to 0 (LUCENE-7996: scorers must not produce
 * negative scores), but its clamp tests {@code score >= 0}, which is true for -0.0, so a payload
 * score of -0.0 is returned as the document score.
 */
public class TestPayloadScoreQueryNegativeZero extends LuceneTestCase {

  /** One token "x" whose payload is the 4-byte float written by PayloadHelper.encodeFloat. */
  private static final class OneToken extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PayloadAttribute payload = addAttribute(PayloadAttribute.class);
    private final float value;
    private boolean done;

    OneToken(float value) {
      this.value = value;
    }

    @Override
    public boolean incrementToken() {
      if (done) {
        return false;
      }
      clearAttributes();
      term.append("x");
      payload.setPayload(new BytesRef(PayloadHelper.encodeFloat(value)));
      done = true;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      done = false;
    }
  }

  /** Decodes the float payloads written by PayloadHelper.encodeFloat (FloatEncoder). */
  private static final PayloadDecoder IEEE_FLOAT =
      payload -> payload == null ? 1 : PayloadHelper.decodeFloat(payload.bytes, payload.offset);

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (float v : new float[] {-0f, 0f}) { // doc 0: -0.0, doc 1: +0.0
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

  private PayloadScoreQuery query(boolean includeSpanScore) {
    return new PayloadScoreQuery(
        new SpanTermQuery(new Term("f", "x")), new MaxPayloadFunction(), IEEE_FLOAT, includeSpanScore);
  }

  private void assertNonNegative(PayloadScoreQuery q) throws IOException {
    TopDocs td = searcher.search(q, 10);
    assertEquals(2, td.scoreDocs.length);
    for (ScoreDoc sd : td.scoreDocs) {
      assertTrue(
          "doc " + sd.doc + " scored " + sd.score + "; scores must not be negative",
          Float.compare(sd.score, 0f) >= 0);
      float explained = searcher.explain(q, sd.doc).getValue().floatValue();
      assertTrue(
          "doc " + sd.doc + " explained as " + explained, Float.compare(explained, 0f) >= 0);
    }
  }

  public void testPayloadOnlyScoreIsNotNegativeZero() throws IOException {
    assertNonNegative(query(false));
  }

  public void testSpanTimesPayloadScoreIsNotNegativeZero() throws IOException {
    assertNonNegative(query(true));
  }

  /** Both hits score zero; the best hit of a top-1 search must be the first of a top-2 search. */
  public void testTopOneIsHeadOfTopTwo() throws IOException {
    PayloadScoreQuery q = query(false);
    ScoreDoc[] top1 = searcher.search(q, 1).scoreDocs;
    ScoreDoc[] top2 = searcher.search(q, 2).scoreDocs;
    assertEquals(1, top1.length);
    assertEquals(2, top2.length);
    assertEquals(
        "top-1 " + top1[0].doc + " (" + top1[0].score + ") vs top-2 [" + top2[0].doc + " ("
            + top2[0].score + "), " + top2[1].doc + " (" + top2[1].score + ")]",
        top2[0].doc,
        top1[0].doc);
  }
}
