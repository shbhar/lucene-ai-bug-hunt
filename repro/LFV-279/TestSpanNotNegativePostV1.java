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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of SpanNotQuery with a negative {@code post}. Public API only.
 *
 * <p>Constructor javadoc: matches spans from include "which have no overlap with spans from exclude
 * within pre tokens before or post tokens of include. Inversely, negative values for pre and/or
 * post allow a certain amount of overlap to occur." A negative post can therefore only relax the
 * exclusion: every span accepted with post = 0 must also be accepted with post &lt; 0.
 */
public class TestSpanNotNegativePostV1 extends LuceneTestCase {
  private static final String F = "body";

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    super.tearDown();
  }

  private void index(String... docs) throws IOException {
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      for (String d : docs) {
        Document doc = new Document();
        doc.add(new TextField(F, d, Field.Store.YES));
        w.addDocument(doc);
      }
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  private static SpanQuery term(String t) {
    return new SpanTermQuery(new Term(F, t));
  }

  /** All spans of q, keyed by the stored text of the document they occur in. */
  private Map<String, List<String>> spansByText(SpanQuery q) throws IOException {
    SpanQuery rq = (SpanQuery) searcher.rewrite(q);
    SpanWeight w = rq.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    Map<String, List<String>> out = new TreeMap<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      Spans s = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
      if (s == null) {
        continue;
      }
      while (s.nextDoc() != DocIdSetIterator.NO_MORE_DOCS) {
        String text = ctx.reader().storedFields().document(s.docID()).get(F);
        List<String> l = out.computeIfAbsent(text, k -> new ArrayList<>());
        while (s.nextStartPosition() != Spans.NO_MORE_POSITIONS) {
          l.add("[" + s.startPosition() + "," + s.endPosition() + ")");
        }
      }
    }
    return out;
  }

  /** Doc "stop go go end": both "go" spans have no overlap with "stop" (position 0). */
  public void testSpansWithNegativePost() throws IOException {
    index("stop go go end");
    List<String> both = Arrays.asList("[1,2)", "[2,3)");
    // control: post = 0 keeps both "go" spans
    assertEquals(both, spansByText(new SpanNotQuery(term("go"), term("stop"), 0, 0)).get("stop go go end"));
    for (int post : new int[] {-1, -2, -3}) {
      assertEquals(
          "pre = 0, post = " + post,
          both,
          spansByText(new SpanNotQuery(term("go"), term("stop"), 0, post)).get("stop go go end"));
    }
    // the dist constructor (pre = post = -1)
    assertEquals(
        "dist = -1", both, spansByText(new SpanNotQuery(term("go"), term("stop"), -1)).get("stop go go end"));
  }

  /** Document-level effect when the SpanNotQuery is nested in another positional query. */
  public void testDocumentLevelNested() throws IOException {
    index("stop go go end");
    for (int post : new int[] {0, -1}) {
      SpanQuery not = new SpanNotQuery(term("go"), term("stop"), 0, post);
      SpanQuery near = new SpanNearQuery(new SpanQuery[] {not, term("end")}, 0, true);
      assertEquals("spanNear([spanNot(go, stop, 0, " + post + "), end], 0, true)", 1, searcher.count(near));
      SpanQuery range = new SpanPositionRangeQuery(not, 2, 3);
      assertEquals("spanPosRange(spanNot(go, stop, 0, " + post + "), 2, 3)", 1, searcher.count(range));
    }
  }

  /**
   * Bounded enumeration: every document over {a, b} of 1..5 tokens, include/exclude one of the two
   * terms, pre and post in -2..2, compared against the reference reading of the javadoc: an include
   * span s is removed iff some exclude span e satisfies e.end &gt; s.start - pre and e.start &lt;
   * s.end + post (e meets the window [s.start - pre, s.end + post)).
   */
  public void testSmallEnumerationAgainstReference() throws IOException {
    List<String> docs = new ArrayList<>();
    for (int len = 1; len <= 5; len++) {
      for (int mask = 0; mask < (1 << len); mask++) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
          if (i > 0) {
            sb.append(' ');
          }
          sb.append(((mask >> i) & 1) == 0 ? "a" : "b");
        }
        docs.add(sb.toString());
      }
    }
    index(docs.toArray(new String[0]));

    int checks = 0;
    int mismatches = 0;
    int mismatchesPostNonNegative = 0;
    int mismatchesNotAfterExhaustion = 0;
    List<String> examples = new ArrayList<>();
    String[][] pairs = {{"a", "b"}, {"b", "a"}};
    for (String[] p : pairs) {
      for (int pre = -2; pre <= 2; pre++) {
        for (int post = -2; post <= 2; post++) {
          Map<String, List<String>> actual =
              spansByText(new SpanNotQuery(term(p[0]), term(p[1]), pre, post));
          for (String d : docs) {
            List<String> tokens = Arrays.asList(d.split(" "));
            List<String> expected = new ArrayList<>();
            int prevIncludeStart = -1;
            for (int s = 0; s < tokens.size(); s++) {
              if (tokens.get(s).equals(p[0]) == false) {
                continue;
              }
              boolean removed = false;
              boolean excludeExhaustedByEarlierInclude = prevIncludeStart >= 0;
              for (int e = 0; e < tokens.size(); e++) {
                if (tokens.get(e).equals(p[1]) == false) {
                  continue;
                }
                if (e + 1 > s - pre && e < s + 1 + post) {
                  removed = true;
                }
                if (prevIncludeStart < 0 || e + 1 > prevIncludeStart - pre) {
                  excludeExhaustedByEarlierInclude = false;
                }
              }
              String span = "[" + s + "," + (s + 1) + ")";
              if (removed == false) {
                expected.add(span);
              }
              List<String> act = actual.getOrDefault(d, List.of());
              checks++;
              if (act.contains(span) != (removed == false)) {
                mismatches++;
                if (post >= 0) {
                  mismatchesPostNonNegative++;
                }
                if (excludeExhaustedByEarlierInclude == false) {
                  mismatchesNotAfterExhaustion++;
                }
                if (examples.size() < 5) {
                  examples.add(
                      "doc '" + d + "' spanNot(" + p[0] + ", " + p[1] + ", " + pre + ", " + post
                          + ") span " + span + ": expected " + (removed ? "removed" : "kept")
                          + ", actual " + (act.contains(span) ? "kept" : "removed"));
                }
              }
              prevIncludeStart = s;
            }
          }
        }
      }
    }
    assertEquals(
        "checks="
            + checks
            + " mismatches="
            + mismatches
            + " (with post>=0: "
            + mismatchesPostNonNegative
            + ", not after exclude exhausted by an earlier include span: "
            + mismatchesNotAfterExhaustion
            + ") examples="
            + examples,
        0,
        mismatches);
  }
}
