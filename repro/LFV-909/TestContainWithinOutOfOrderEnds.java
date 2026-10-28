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
import java.util.Map;
import java.util.TreeMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SpanContainingQuery(big, little) must return every span of big that contains at least one span
 * of little; SpanWithinQuery(big, little) must return every span of little that is inside a span
 * of big (class javadocs). The reference below is computed from the spans that big and little
 * themselves enumerate, so it only checks the containment step.
 *
 * <p>A SpanOrQuery can legally produce spans whose end positions are not monotone (spans are
 * ordered by start, then end): a long alternative followed by a shorter one that starts later.
 */
public class TestContainWithinOutOfOrderEnds extends LuceneTestCase {

  private static final String F = "f";

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;

  private static SpanQuery t(String s) {
    return new SpanTermQuery(new Term(F, s));
  }

  private static SpanQuery ord(int slop, SpanQuery... clauses) {
    return new SpanNearQuery(clauses, slop, true);
  }

  private static SpanQuery unord(int slop, SpanQuery... clauses) {
    return new SpanNearQuery(clauses, slop, false);
  }

  private static SpanQuery or(SpanQuery... clauses) {
    return new SpanOrQuery(clauses);
  }

  private void index(List<String> docs) throws IOException {
    dir = newDirectory();
    IndexWriter w =
        new IndexWriter(
            dir,
            newIndexWriterConfig(new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false))
                // keep doc ids in insertion order (no merges, so no reordering)
                .setMergePolicy(NoMergePolicy.INSTANCE));
    for (String text : docs) {
      Document d = new Document();
      d.add(new Field(F, text, TextField.TYPE_NOT_STORED));
      w.addDocument(d);
    }
    w.close();
    reader = DirectoryReader.open(dir);
    assertEquals(docs.size(), reader.maxDoc());
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    if (reader != null) {
      reader.close();
    }
    if (dir != null) {
      dir.close();
    }
    reader = null;
    dir = null;
    searcher = null;
    super.tearDown();
  }

  /** Spans (start, end) per document, as Lucene enumerates them for q; key = global doc id. */
  private Map<Integer, List<List<Integer>>> spans(SpanQuery q) throws IOException {
    SpanQuery rewritten = (SpanQuery) searcher.rewrite(q);
    SpanWeight w = rewritten.createWeight(searcher, ScoreMode.COMPLETE_NO_SCORES, 1f);
    Map<Integer, List<List<Integer>>> out = new TreeMap<>();
    for (LeafReaderContext ctx : reader.leaves()) {
      Spans sp = w.getSpans(ctx, SpanWeight.Postings.POSITIONS);
      if (sp == null) {
        continue;
      }
      for (int doc = sp.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = sp.nextDoc()) {
        List<List<Integer>> list = new ArrayList<>();
        for (int s = sp.nextStartPosition();
            s != Spans.NO_MORE_POSITIONS;
            s = sp.nextStartPosition()) {
          list.add(List.of(s, sp.endPosition()));
        }
        if (list.isEmpty() == false) {
          out.put(ctx.docBase + doc, list);
        }
      }
    }
    return out;
  }

  private static boolean inside(List<Integer> outer, List<Integer> inner) {
    return outer.get(0) <= inner.get(0) && inner.get(1) <= outer.get(1);
  }

  /** Reference: spans of big that contain some span of little. */
  private static Map<Integer, List<List<Integer>>> refContaining(
      Map<Integer, List<List<Integer>>> big, Map<Integer, List<List<Integer>>> little) {
    Map<Integer, List<List<Integer>>> out = new TreeMap<>();
    for (Map.Entry<Integer, List<List<Integer>>> e : big.entrySet()) {
      List<List<Integer>> ls = little.get(e.getKey());
      if (ls == null) {
        continue;
      }
      List<List<Integer>> keep = new ArrayList<>();
      for (List<Integer> b : e.getValue()) {
        for (List<Integer> l : ls) {
          if (inside(b, l)) {
            keep.add(b);
            break;
          }
        }
      }
      if (keep.isEmpty() == false) {
        out.put(e.getKey(), keep);
      }
    }
    return out;
  }

  /** Reference: spans of little that are inside some span of big. */
  private static Map<Integer, List<List<Integer>>> refWithin(
      Map<Integer, List<List<Integer>>> big, Map<Integer, List<List<Integer>>> little) {
    Map<Integer, List<List<Integer>>> out = new TreeMap<>();
    for (Map.Entry<Integer, List<List<Integer>>> e : little.entrySet()) {
      List<List<Integer>> bs = big.get(e.getKey());
      if (bs == null) {
        continue;
      }
      List<List<Integer>> keep = new ArrayList<>();
      for (List<Integer> l : e.getValue()) {
        for (List<Integer> b : bs) {
          if (inside(b, l)) {
            keep.add(l);
            break;
          }
        }
      }
      if (keep.isEmpty() == false) {
        out.put(e.getKey(), keep);
      }
    }
    return out;
  }

  private void assertContainmentQueries(SpanQuery big, SpanQuery little) throws IOException {
    Map<Integer, List<List<Integer>>> b = spans(big);
    Map<Integer, List<List<Integer>>> l = spans(little);
    SpanQuery containing = new SpanContainingQuery(big, little);
    SpanQuery within = new SpanWithinQuery(big, little);
    Map<Integer, List<List<Integer>>> expC = refContaining(b, l);
    Map<Integer, List<List<Integer>>> expW = refWithin(b, l);
    String ctx = "big spans=" + b + " little spans=" + l;
    assertEquals(containing + " spans; " + ctx, expC, spans(containing));
    assertEquals(within + " spans; " + ctx, expW, spans(within));
    assertEquals(containing + " count; " + ctx, expC.size(), searcher.count(containing));
    assertEquals(within + " count; " + ctx, expW.size(), searcher.count(within));
  }

  /** big = "x y" [0,2); little = (x NEAR/1 z) OR y = [0,3), [1,2); y is inside "x y". */
  public void testLongerLittleWithSameStartHidesContainedOne() throws IOException {
    index(List.of("x y z w"));
    SpanQuery big = ord(0, t("x"), t("y"));
    SpanQuery little = or(ord(1, t("x"), t("z")), t("y"));
    assertEquals(Map.of(0, List.of(List.of(0, 2))), spans(big));
    assertEquals(Map.of(0, List.of(List.of(0, 3), List.of(1, 2))), spans(little));
    assertContainmentQueries(big, little);
  }

  /** big = (x NEAR/1 z) [0,3); little = (y NEAR/1 w) OR z = [1,4), [2,3); z is inside [0,3). */
  public void testLongerLittleWithLaterStartHidesContainedOne() throws IOException {
    index(List.of("x y z w"));
    SpanQuery big = ord(1, t("x"), t("z"));
    SpanQuery little = or(ord(1, t("y"), t("w")), t("z"));
    assertEquals(Map.of(0, List.of(List.of(0, 3))), spans(big));
    assertEquals(Map.of(0, List.of(List.of(1, 4), List.of(2, 3))), spans(little));
    assertContainmentQueries(big, little);
  }

  /**
   * big = "y z" [1,3); little = (x NEAR/2 w) OR z = [0,4), [2,3). Doc 0 lacks x and w, doc 1 has
   * them: adding an alternative of a SpanOrQuery must not remove a match. Also SpanWithinQuery and
   * SpanContainingQuery must agree at document level (as TestSpanSearchEquivalence asserts).
   */
  public void testWithinLosesMatchWhenOrGainsLongAlternative() throws IOException {
    index(List.of("q y z q", "x y z w"));
    SpanQuery big = ord(0, t("y"), t("z"));
    SpanQuery little = or(ord(2, t("x"), t("w")), t("z"));
    assertEquals(
        Map.of(0, List.of(List.of(1, 3)), 1, List.of(List.of(1, 3))), spans(big));
    assertEquals(
        Map.of(0, List.of(List.of(2, 3)), 1, List.of(List.of(0, 4), List.of(2, 3))),
        spans(little));
    assertEquals(
        "within vs containing (doc level)",
        searcher.count(new SpanContainingQuery(big, little)),
        searcher.count(new SpanWithinQuery(big, little)));
    assertContainmentQueries(big, little);
  }

  /** Control: the same documents with little ends in order (only the short alternative). */
  public void testControlMonotoneLittleEnds() throws IOException {
    index(List.of("x y z w"));
    assertContainmentQueries(ord(0, t("x"), t("y")), t("y"));
    assertContainmentQueries(ord(1, t("x"), t("z")), or(t("z"), t("w")));
    assertContainmentQueries(ord(0, t("y"), t("z")), or(t("x"), t("z")));
  }

  private static final SpanQuery[] QUERIES =
      new SpanQuery[] {
        t("a"),
        t("b"),
        t("c"),
        ord(0, t("a"), t("b")),
        ord(1, t("a"), t("c")),
        unord(1, t("a"), t("b")),
        or(t("a"), t("b")),
        or(ord(1, t("a"), t("b")), t("c")),
        or(ord(2, t("a"), t("c")), t("b")),
        or(unord(1, t("b"), t("c")), t("a")),
        or(t("c"), ord(3, t("a"), t("b"))),
      };

  private static List<String> allDocs() {
    List<String> docs = new ArrayList<>();
    String[] alphabet = {"a", "b", "c"};
    for (int len = 1; len <= 5; len++) {
      int n = 1;
      for (int i = 0; i < len; i++) {
        n *= alphabet.length;
      }
      for (int code = 0; code < n; code++) {
        StringBuilder sb = new StringBuilder();
        int c = code;
        for (int i = 0; i < len; i++) {
          if (i > 0) {
            sb.append(' ');
          }
          sb.append(alphabet[c % alphabet.length]);
          c /= alphabet.length;
        }
        docs.add(sb.toString());
      }
    }
    return docs;
  }

  private static boolean monotoneEnds(List<List<Integer>> spans) {
    for (int i = 1; i < spans.size(); i++) {
      if (spans.get(i).get(1) < spans.get(i - 1).get(1)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Counters: [0] checks, [1] missing (doc, query) results with monotone little ends, [2] missing
   * with non-monotone little ends, [3] extra results, [4] queries whose count() is wrong, [5]
   * documents missed entirely (subset of [2]).
   */
  private long[] exhaustive(List<String> examples) throws IOException {
    List<String> docs = allDocs();
    index(docs);
    long[] c = new long[6];
    for (SpanQuery big : QUERIES) {
      for (SpanQuery little : QUERIES) {
        Map<Integer, List<List<Integer>>> b = spans(big);
        Map<Integer, List<List<Integer>>> l = spans(little);
        for (int kind = 0; kind < 2; kind++) {
          SpanQuery q =
              kind == 0 ? new SpanContainingQuery(big, little) : new SpanWithinQuery(big, little);
          Map<Integer, List<List<Integer>>> exp =
              kind == 0 ? refContaining(b, l) : refWithin(b, l);
          Map<Integer, List<List<Integer>>> act = spans(q);
          for (int doc = 0; doc < docs.size(); doc++) {
            c[0]++;
            List<List<Integer>> e = exp.get(doc);
            List<List<Integer>> a = act.get(doc);
            if (e == null ? a == null : e.equals(a)) {
              continue;
            }
            if (e == null || (a != null && e.containsAll(a) == false)) {
              c[3]++;
            } else if (monotoneEnds(l.get(doc))) {
              c[1]++;
            } else {
              c[2]++;
              if (a == null) {
                c[5]++;
              }
            }
            if (examples.size() < 5) {
              examples.add(
                  q
                      + " doc '"
                      + docs.get(doc)
                      + "' big="
                      + b.get(doc)
                      + " little="
                      + l.get(doc)
                      + " expected="
                      + e
                      + " actual="
                      + a);
            }
          }
          if (searcher.count(q) != exp.size()) {
            c[4]++;
          }
        }
      }
    }
    return c;
  }

  /** Every containment result over all documents of length 1..5 over {a, b, c}. */
  public void testExhaustiveSmallDocsAgreeWithContract() throws IOException {
    List<String> examples = new ArrayList<>();
    long[] c = exhaustive(examples);
    String msg =
        "checks="
            + c[0]
            + " missingMonotoneLittle="
            + c[1]
            + " missingNonMonotoneLittle="
            + c[2]
            + " (whole doc missed="
            + c[5]
            + ")"
            + " extra="
            + c[3]
            + " queriesWithWrongCount="
            + c[4]
            + " examples="
            + examples;
    System.out.println("EXHAUSTIVE " + msg);
    assertEquals(msg, 0, c[1] + c[2] + c[3] + c[4]);
  }

  /** Characterisation: whenever little's ends are in order, both queries are exact. */
  public void testExhaustiveMonotoneLittleEndsAreExact() throws IOException {
    List<String> examples = new ArrayList<>();
    long[] c = exhaustive(examples);
    assertEquals("extra results " + examples, 0, c[3]);
    assertEquals("missing with monotone little ends " + examples, 0, c[1]);
  }
}
