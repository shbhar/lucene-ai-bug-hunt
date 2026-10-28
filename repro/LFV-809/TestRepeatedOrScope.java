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
package org.apache.lucene.queries.intervals;

import static org.apache.lucene.queries.intervals.Intervals.maxgaps;
import static org.apache.lucene.queries.intervals.Intervals.or;
import static org.apache.lucene.queries.intervals.Intervals.ordered;
import static org.apache.lucene.queries.intervals.Intervals.phrase;
import static org.apache.lucene.queries.intervals.Intervals.term;
import static org.apache.lucene.queries.intervals.Intervals.unordered;

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Scope probe, prints one line per case and always passes: a run of the same disjunction inside a
 * longer ordered/unordered, larger repeats, alternatives that overlap, and the over-match guards.
 * X = or(b, "c b").
 */
public class TestRepeatedOrScope extends LuceneTestCase {

  private static final String FIELD = "f";

  private static IntervalsSource x() {
    return or(term("b"), phrase("c", "b"));
  }

  private static int count(IntervalsSource source, String text) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField(FIELD, text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return new IndexSearcher(reader).count(new IntervalQuery(FIELD, source));
      }
    }
  }

  private static void p(String label, IntervalsSource s, String doc, int expected)
      throws IOException {
    int hits = count(s, doc);
    System.out.println(
        "SCOPE\t"
            + label
            + "\tdoc='"
            + doc
            + "'\texpected="
            + expected
            + "\thits="
            + hits
            + "\t"
            + (hits == expected ? "ok" : "WRONG"));
  }

  public void testScope() throws IOException {
    IntervalsSource d = term("d");
    // a run of the same disjunction inside a longer ordered / unordered
    p("S1 maxgaps0 ordered(X,X,d)", maxgaps(0, ordered(x(), x(), d)), "c b c b d", 1);
    p("S2 maxgaps0 unordered(X,X,d)", maxgaps(0, unordered(x(), x(), d)), "c b c b d", 1);
    p("S3 maxgaps0 unordered(X,X,d) no gap-free pair", maxgaps(0, unordered(x(), x(), d)), "b x c b d", 0);
    // three copies
    p("S4 maxgaps0 unordered(X,X,X)", maxgaps(0, unordered(x(), x(), x())), "b c b c b", 1);
    p("S5 maxgaps0 unordered(X,X,X) two occurrences", maxgaps(0, unordered(x(), x(), x())), "c b c b", 0);
    // alternatives that overlap each other: or("a b", "b c") twice on 'a b c'; whether such copies
    // may share a position is a design question, the expected value is what main returns today
    IntervalsSource o = or(phrase("a", "b"), phrase("b", "c"));
    p("S6 unchanged: unordered(O,O), copies share b", unordered(o, o), "a b c", 1);
    p("S7 unchanged: maxgaps3 unordered(O,O), copies share b", maxgaps(3, unordered(o, o)), "a b c", 1);
    // over-match guards
    p("S8 maxgaps0 unordered(X,X) one occurrence", maxgaps(0, unordered(x(), x())), "c b", 0);
    p("S9 maxgaps0 ordered(X,X) one occurrence", maxgaps(0, ordered(x(), x())), "c b", 0);
    p("S10 maxgaps0 unordered(X,X) with a gap", maxgaps(0, unordered(x(), x())), "b x c b", 0);
    p("S11 maxgaps1 unordered(X,X) with a gap", maxgaps(1, unordered(x(), x())), "b x c b", 1);
    // the workaround: ordered with an equivalent but unequal second source
    IntervalsSource xz = or(term("b"), phrase("c", "b"), term("zzz"));
    p("S12 workaround maxgaps0 ordered(X,X+zzz)", maxgaps(0, ordered(x(), xz)), "c b c b", 1);
    p("S13 workaround maxgaps0 ordered(X,X+zzz)", maxgaps(0, ordered(x(), xz)), "c b", 0);
  }
}
