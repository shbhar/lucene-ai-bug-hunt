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
package org.apache.lucene.queries.spans;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanNotQuery documents a negative post (or dist) as "allow a certain amount of overlap", so it
 * must keep every include span that post = 0 keeps.
 */
public class TestSpanNotNegativePost extends LuceneTestCase {

  private static SpanQuery t(String text) {
    return new SpanTermQuery(new Term("f", text));
  }

  private static SpanQuery notB(int pre, int post) {
    return new SpanNotQuery(t("a"), t("b"), pre, post);
  }

  private static SpanQuery nearC(SpanQuery first) {
    return new SpanNearQuery(new SpanQuery[] {first, t("c")}, 0, true);
  }

  /** The spans of {@code q} in the index's only document, as "[start,end)". */
  private static List<String> spans(IndexSearcher searcher, SpanQuery q) throws IOException {
    SpanQuery rewritten = (SpanQuery) searcher.rewrite(q);
    SpanWeight w = rewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    Spans spans =
        w.getSpans(searcher.getIndexReader().leaves().get(0), SpanWeight.Postings.POSITIONS);
    List<String> out = new ArrayList<>();
    if (spans != null && spans.nextDoc() == 0) {
      while (spans.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
        out.add("[" + spans.startPosition() + "," + spans.endPosition() + ")");
      }
    }
    return out;
  }

  public void testIncludeSpansAfterLastExclude() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(new MockAnalyzer(random()));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        Document doc = new Document();
        // positions: b=0, a=1, a=2, c=3
        doc.add(new TextField("f", "b a a c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = newSearcher(reader);
        // "b" at 0 overlaps neither "a", so every window keeps both "a" spans
        List<String> post0 = spans(searcher, notB(0, 0));
        List<String> postMinus1 = spans(searcher, notB(0, -1));
        List<String> distMinus1 = spans(searcher, new SpanNotQuery(t("a"), t("b"), -1));
        // nested: "a c" at positions 2..3
        int near0 = searcher.count(nearC(notB(0, 0)));
        int nearMinus1 = searcher.count(nearC(notB(0, -1)));
        String observed =
            "spans: post 0 "
                + post0
                + ", post -1 "
                + postMinus1
                + ", dist -1 "
                + distMinus1
                + "; spanNear([spanNot(a, b), c]) count: post 0 "
                + near0
                + ", post -1 "
                + nearMinus1;
        assertEquals(observed, List.of("[1,2)", "[2,3)"), post0); // control
        assertEquals(1, near0); // control
        assertEquals(observed, post0, postMinus1);
        assertEquals(observed, post0, distMinus1);
        assertEquals(observed, 1, nearMinus1);
      }
    }
  }
}
