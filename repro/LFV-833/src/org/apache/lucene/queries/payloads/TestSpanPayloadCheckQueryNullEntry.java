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
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PayloadAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanNearQuery;
import org.apache.lucene.queries.spans.SpanQuery;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * SpanPayloadCheckQuery's matcher treats a null entry of payloadToMatch as "this position has no
 * payload" (PayloadChecker.collectLeaf), so such a query searches correctly, but its toString()
 * passes the null entry to Term.toString(BytesRef), which throws NullPointerException; explain()
 * puts the query's string into its description and throws too.
 */
public class TestSpanPayloadCheckQueryNullEntry extends LuceneTestCase {

  /** Tokens "a b": "a" has no payload, "b" has the payload "p". */
  private static final class TwoTokens extends TokenStream {
    private final CharTermAttribute term = addAttribute(CharTermAttribute.class);
    private final PayloadAttribute payload = addAttribute(PayloadAttribute.class);
    private int upto;

    @Override
    public boolean incrementToken() {
      if (upto == 2) {
        return false;
      }
      clearAttributes();
      if (upto == 0) {
        term.append("a");
      } else {
        term.append("b");
        payload.setPayload(new BytesRef("p"));
      }
      upto++;
      return true;
    }

    @Override
    public void reset() throws IOException {
      super.reset();
      upto = 0;
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
      Document doc = new Document();
      doc.add(new TextField("f", new TwoTokens()));
      w.addDocument(doc);
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

  /** "a" without a payload followed by "b" with payload "p". */
  private static SpanPayloadCheckQuery query() {
    SpanQuery near =
        new SpanNearQuery(
            new SpanQuery[] {new SpanTermQuery(new Term("f", "a")), new SpanTermQuery(new Term("f", "b"))},
            0,
            true);
    List<BytesRef> payloads = Arrays.asList(null, new BytesRef("p"));
    return new SpanPayloadCheckQuery(near, payloads);
  }

  /** Control: the null entry is honoured by the matcher, the document matches. */
  public void testSearchMatches() throws IOException {
    assertEquals(1, searcher.count(query()));
    // and a non-null first entry does not match, since "a" has no payload
    SpanQuery near =
        new SpanNearQuery(
            new SpanQuery[] {new SpanTermQuery(new Term("f", "a")), new SpanTermQuery(new Term("f", "b"))},
            0,
            true);
    assertEquals(
        0,
        searcher.count(
            new SpanPayloadCheckQuery(near, Arrays.asList(new BytesRef("p"), new BytesRef("p")))));
  }

  public void testToString() {
    String s = query().toString();
    assertTrue(s, s.startsWith("SpanPayloadCheckQuery("));
  }

  public void testExplain() throws IOException {
    Explanation e = searcher.explain(query(), 0);
    assertTrue(e.toString(), e.isMatch());
  }
}
