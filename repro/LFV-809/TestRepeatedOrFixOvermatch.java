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
 * Over-match probe for fix A, prints one line per case and always passes. W = or(b, unordered(a,
 * b)): an alternative whose own intervals can overlap each other. On 'a b a' W has the intervals
 * b [1,1], unordered(a, b) [0,1] and [1,2]; minimization keeps only b [1,1], and no two
 * occurrences of W are disjoint, so a query that needs two occurrences of W must not match.
 */
public class TestRepeatedOrFixOvermatch extends LuceneTestCase {

  private static final String FIELD = "f";

  private static IntervalsSource w() {
    return or(term("b"), unordered(term("a"), term("b")));
  }

  private static IntervalsSource wz() {
    return or(term("b"), unordered(term("a"), term("b")), term("zzz"));
  }

  private static IntervalsSource p() {
    return unordered(term("a"), term("b"));
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

  private static void line(String label, IntervalsSource s, String doc, int expected)
      throws IOException {
    int hits = count(s, doc);
    System.out.println(
        "OVER\t"
            + label
            + "\tdoc='"
            + doc
            + "'\tsource="
            + s
            + "\texpected="
            + expected
            + "\thits="
            + hits
            + "\t"
            + (hits == expected ? "ok" : "WRONG"));
  }

  public void testOvermatch() throws IOException {
    // the counterexample from the cross-model fact check: phrase(z, ordered(W, W)) on 'z a b a'
    line("O1 ordered(W,W) unfiltered", ordered(w(), w()), "z a b a", 0);
    line("O2 phrase(z, ordered(W,W))", phrase(term("z"), ordered(w(), w())), "z a b a", 0);
    line("O3 control phrase(z, ordered(W,W+zzz))", phrase(term("z"), ordered(w(), wz())), "z a b a", 0);
    // the same pair under maxgaps, ordered and unordered
    line("O4 unordered(W,W) unfiltered", unordered(w(), w()), "a b a", 0);
    line("O5 maxgaps0 unordered(W,W)", maxgaps(0, unordered(w(), w())), "a b a", 0);
    line("O6 maxgaps0 ordered(W,W)", maxgaps(0, ordered(w(), w())), "a b a", 0);
    line("O7 control maxgaps0 ordered(W,W+zzz)", maxgaps(0, ordered(w(), wz())), "a b a", 0);
    // the regrouped pair on its own: ordered(P, P), P = unordered(a, b) (LFV-807's over-match)
    line("O8 ordered(P,P)", ordered(p(), p()), "a b a", 0);
    // the bug's own example still matches
    IntervalsSource x = or(term("b"), phrase("c", "b"));
    line("O9 maxgaps0 unordered(X,X)", maxgaps(0, unordered(x, or(term("b"), phrase("c", "b")))), "c b c b", 1);
  }
}
