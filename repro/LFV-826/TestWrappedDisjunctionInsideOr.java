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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Intervals.or "Automatically rewrites if wrapped by an interval source that is sensitive to
 * internal gaps". phrase(x, W, d) with W = maxwidth(5, or("b c", b)) (also extend(.., 0, 0) or
 * fixField(..)) is rewritten and matches 'x b c d'. Putting W as one alternative of another or()
 * - phrase(x, or(W, y), d), with a 'y' that occurs nowhere - must not lose that match, but the
 * rewrite does not look into W any more: the outer or's pullUpDisjunctions returns W as a whole,
 * W has minExtent 1, so it is grouped with the single-term alternatives and its 'b c' interval is
 * minimized away.
 */
public class TestWrappedDisjunctionInsideOr extends LuceneTestCase {

  private static final String FIELD = "f";

  private static void check(IndexSearcher searcher, String name, IntervalsSource w)
      throws IOException {
    IntervalsSource x = Intervals.term("x"), d = Intervals.term("d"), y = Intervals.term("y");
    assertEquals(
        "control phrase(x, " + name + ", d)",
        1,
        searcher.count(new IntervalQuery(FIELD, Intervals.phrase(x, w, d))));
    assertEquals(
        "phrase(x, or(" + name + ", y), d) must match like phrase(x, " + name + ", d): adding an"
            + " alternative that occurs nowhere must not remove the hit",
        1,
        searcher.count(new IntervalQuery(FIELD, Intervals.phrase(x, Intervals.or(w, y), d))));
  }

  private static IntervalsSource bcOrB() {
    return Intervals.or(Intervals.phrase("b", "c"), Intervals.term("b"));
  }

  private IndexReader index(Directory dir) throws IOException {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new TextField(FIELD, "x b c d", Field.Store.NO));
      doc.add(new TextField("g", "x b c d", Field.Store.NO));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  public void testMaxWidthAlternative() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      check(newSearcher(reader), "maxwidth(5, or(\"b c\", b))", Intervals.maxwidth(5, bcOrB()));
    }
  }

  public void testExtendAlternative() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      check(newSearcher(reader), "extend(or(\"b c\", b), 0, 0)", Intervals.extend(bcOrB(), 0, 0));
    }
  }

  public void testFixFieldAlternative() throws IOException {
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      check(newSearcher(reader), "fixField(g, or(\"b c\", b))", Intervals.fixField("g", bcOrB()));
    }
  }

  public void testContainedByReferenceAlternative() throws IOException {
    // the same gap for a filter that pulls up its reference: 'b' is inside 'b c' only
    try (Directory dir = newDirectory();
        IndexReader reader = index(dir)) {
      IndexSearcher searcher = newSearcher(reader);
      IntervalsSource c = Intervals.term("c");
      IntervalsSource w = Intervals.maxwidth(5, bcOrB());
      assertEquals(1, searcher.count(new IntervalQuery(FIELD, Intervals.containedBy(c, w))));
      assertEquals(
          "containedBy(c, or(maxwidth(5, or(\"b c\", b)), y)) must match like containedBy(c,"
              + " maxwidth(5, or(\"b c\", b)))",
          1,
          searcher.count(
              new IntervalQuery(
                  FIELD, Intervals.containedBy(c, Intervals.or(w, Intervals.term("y"))))));
    }
  }
}
