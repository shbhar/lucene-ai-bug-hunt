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
package org.apache.lucene.queries.intervals.v1check;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.queries.intervals.IntervalIterator;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Matches;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreMode;
import org.apache.lucene.search.Weight;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.LuceneTestCase.SuppressSysoutChecks;

/**
 * Independent check of Intervals.before(source, reference) when a reference interval starts at the
 * first position of the field. Public API only (this class is not in the intervals package).
 *
 * <p>Javadoc of Intervals.before: "Returns intervals from the source that appear before intervals
 * from the reference". At every position p > 0 the implementation requires source.end < ref.start
 * (strict), so the outcome must not change when the same token sequence is shifted right by one
 * position.
 */
@SuppressSysoutChecks(bugUrl = "verification prints a summary")
public class TestBeforeFirstPositionV1 extends LuceneTestCase {

  private static final String F = "f";

  /** Indexes the given texts, one document each, with an "id" doc value equal to the index. */
  private static Directory index(String... texts) throws IOException {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer(CharArraySet.EMPTY_SET));
    try (IndexWriter w = new IndexWriter(dir, iwc)) {
      for (int i = 0; i < texts.length; i++) {
        Document d = new Document();
        d.add(new TextField(F, texts[i], Field.Store.NO));
        d.add(new NumericDocValuesField("id", i));
        w.addDocument(d);
      }
    }
    return dir;
  }

  /** Returns, for each text index, whether the IntervalQuery for the source matches it. */
  private static boolean[] matches(IndexSearcher s, IntervalsSource src, int n) throws IOException {
    boolean[] out = new boolean[n];
    for (LeafReaderContext ctx : s.getIndexReader().leaves()) {
      IntervalIterator it = src.intervals(F, ctx);
      if (it == null) {
        continue;
      }
      NumericDocValues ids = ctx.reader().getNumericDocValues("id");
      for (int doc = it.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = it.nextDoc()) {
        if (it.nextInterval() != IntervalIterator.NO_MORE_INTERVALS) {
          assertTrue(ids.advanceExact(doc));
          out[(int) ids.longValue()] = true;
        }
      }
    }
    return out;
  }

  private static int count(IndexSearcher s, IntervalsSource src) throws IOException {
    return s.count(new IntervalQuery(F, src));
  }

  /**
   * Shift invariance: before(york, york) on "york" and on "x york". Neither document has a york
   * before another york, so both must have 0 hits. Control: "york york" must match.
   */
  public void testTermBeforeItselfShiftInvariance() throws IOException {
    String[] texts = {"york", "x york", "york york"};
    try (Directory dir = index(texts);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = newSearcher(r);
      IntervalsSource q = Intervals.before(Intervals.term("york"), Intervals.term("york"));
      boolean[] m = matches(s, q, texts.length);
      System.out.println(
          "before(york,york): 'york'=" + m[0] + " 'x york'=" + m[1] + " 'york york'=" + m[2]);
      assertTrue("control: 'york york' must match", m[2]);
      assertFalse("control: 'x york' must not match", m[1]);
      assertFalse(
          "'york' has nothing before the only york, yet before(york,york) matches it", m[0]);
    }
  }

  /**
   * A source term that is the first word of a reference phrase is not before that phrase. Shifted
   * copy "x new york" must give the same (empty) answer as "new york".
   */
  public void testFirstWordOfPhraseReference() throws IOException {
    String[] texts = {"new york", "x new york", "new x new york"};
    try (Directory dir = index(texts);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = newSearcher(r);
      IntervalsSource q =
          Intervals.before(Intervals.term("new"), Intervals.phrase("new", "york"));
      boolean[] m = matches(s, q, texts.length);
      System.out.println(
          "before(new,phrase(new york)): 'new york'="
              + m[0]
              + " 'x new york'="
              + m[1]
              + " 'new x new york'="
              + m[2]
              + " count="
              + count(s, q));
      assertTrue("control: 'new x new york' must match", m[2]);
      assertFalse("control: 'x new york' must not match", m[1]);
      assertFalse("'new york': 'new' does not appear before 'new york'", m[0]);
      assertEquals("IntervalQuery hit count", 1, count(s, q));
    }
  }

  /** The Matches API (used by highlighters) reports the same spurious match. */
  public void testMatchesApi() throws IOException {
    try (Directory dir = index("york");
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = newSearcher(r);
      Query q =
          new IntervalQuery(F, Intervals.before(Intervals.term("york"), Intervals.term("york")));
      Weight w = s.createWeight(s.rewrite(q), ScoreMode.COMPLETE_NO_SCORES, 1f);
      LeafReaderContext ctx = s.getIndexReader().leaves().get(0);
      Matches mt = w.matches(ctx, 0);
      assertNull("Matches for before(york,york) on 'york' must be null", mt);
    }
  }

  /**
   * Bounded-exhaustive comparison with a direct oracle: every document of 1..5 tokens over {a,b,c};
   * queries before(t, u) and before(t, phrase(u, v)) for all terms. Oracle: the document matches
   * iff some source occurrence ends strictly before some reference occurrence starts.
   */
  public void testExhaustiveSmallDocs() throws IOException {
    String[] alpha = {"a", "b", "c"};
    List<String[]> docs = new ArrayList<>();
    for (int len = 1; len <= 5; len++) {
      int total = (int) Math.pow(alpha.length, len);
      for (int code = 0; code < total; code++) {
        String[] toks = new String[len];
        int c = code;
        for (int i = 0; i < len; i++) {
          toks[i] = alpha[c % alpha.length];
          c /= alpha.length;
        }
        docs.add(toks);
      }
    }
    String[] texts = new String[docs.size()];
    for (int i = 0; i < texts.length; i++) {
      texts[i] = String.join(" ", docs.get(i));
    }
    int checks = 0, mismatches = 0, spuriousRefAt0SrcAt0 = 0, otherMismatches = 0, missed = 0;
    List<String> examples = new ArrayList<>();
    try (Directory dir = index(texts);
        DirectoryReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = newSearcher(r);
      for (String t : alpha) {
        for (String u : alpha) {
          for (int kind = 0; kind < 1 + alpha.length; kind++) {
            // kind 0: reference = term u; kind k>0: reference = phrase(u, alpha[k-1])
            String v = kind == 0 ? null : alpha[kind - 1];
            IntervalsSource ref =
                v == null ? Intervals.term(u) : Intervals.phrase(Intervals.term(u), Intervals.term(v));
            IntervalsSource q = Intervals.before(Intervals.term(t), ref);
            boolean[] got = matches(s, q, texts.length);
            for (int d = 0; d < texts.length; d++) {
              String[] toks = docs.get(d);
              // reference starts
              List<Integer> refStarts = new ArrayList<>();
              for (int p = 0; p < toks.length; p++) {
                if (toks[p].equals(u) && (v == null || (p + 1 < toks.length && toks[p + 1].equals(v)))) {
                  refStarts.add(p);
                }
              }
              boolean expected = false;
              for (int p = 0; p < toks.length && !expected; p++) {
                if (toks[p].equals(t)) {
                  for (int rs : refStarts) {
                    if (p < rs) {
                      expected = true;
                      break;
                    }
                  }
                }
              }
              checks++;
              if (expected != got[d]) {
                mismatches++;
                boolean refAt0 = !refStarts.isEmpty() && refStarts.get(0) == 0;
                boolean srcAt0 = toks[0].equals(t);
                if (!expected && refAt0 && srcAt0) {
                  spuriousRefAt0SrcAt0++;
                } else if (expected) {
                  missed++;
                } else {
                  otherMismatches++;
                }
                if (examples.size() < 6) {
                  examples.add(q + " on '" + texts[d] + "' expected=" + expected + " got=" + got[d]);
                }
              }
            }
          }
        }
      }
    }
    System.out.println(
        "exhaustive: checks="
            + checks
            + " mismatches="
            + mismatches
            + " spurious(ref starts at 0 and source at 0)="
            + spuriousRefAt0SrcAt0
            + " other spurious="
            + otherMismatches
            + " missed="
            + missed);
    for (String e : examples) {
      System.out.println("  e.g. " + e);
    }
    assertEquals("mismatches not explained by the position-0 case", 0, otherMismatches + missed);
    assertEquals("before() disagrees with the strict-before oracle", 0, mismatches);
  }
}
