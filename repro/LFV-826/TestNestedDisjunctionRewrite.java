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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An or() inside maxwidth(), fixField() or ordered() is rewritten under a gap-sensitive parent. It
 * must still be rewritten when that wrapper is one alternative of another or(): adding an
 * alternative that occurs in no document must not remove a hit. Public API only.
 */
public class TestNestedDisjunctionRewrite extends LuceneTestCase {

  private static final IntervalsSource NOWHERE = Intervals.term("nowhere");

  private static IntervalsSource t(String term) {
    return Intervals.term(term);
  }

  /** or("b c", b): a two-position alternative and a one-position alternative inside it */
  private static IntervalsSource bcOrB() {
    return Intervals.or(Intervals.phrase("b", "c"), t("b"));
  }

  /** Hits of {@code source} on a one-document index with {@code text} in the fields f and g */
  private int count(String text, IntervalsSource source) throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w =
          new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        doc.add(new TextField("g", text, Field.Store.NO));
        w.addDocument(doc);
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        return newSearcher(reader).count(new IntervalQuery("f", source));
      }
    }
  }

  public void testMaxWidth() throws IOException {
    IntervalsSource w = Intervals.maxwidth(5, bcOrB());
    assertEquals(1, count("x b c d", Intervals.phrase(t("x"), w, t("d"))));
    assertEquals(1, count("x b c d", Intervals.phrase(t("x"), Intervals.or(w, NOWHERE), t("d"))));
  }

  public void testFixField() throws IOException {
    IntervalsSource w = Intervals.fixField("g", bcOrB());
    assertEquals(1, count("x b c d", Intervals.phrase(t("x"), w, t("d"))));
    assertEquals(1, count("x b c d", Intervals.phrase(t("x"), Intervals.or(w, NOWHERE), t("d"))));
  }

  public void testContainedBy() throws IOException {
    IntervalsSource w = Intervals.maxwidth(5, bcOrB());
    assertEquals(1, count("x b c d", Intervals.containedBy(t("c"), w)));
    assertEquals(1, count("x b c d", Intervals.containedBy(t("c"), Intervals.or(w, NOWHERE))));
  }

  public void testOrdered() throws IOException {
    // the alternative has minExtent 2, so it is not grouped with the single-position ones
    IntervalsSource o = Intervals.ordered(Intervals.or(t("a"), Intervals.phrase("b", "a")), t("c"));
    assertEquals(1, count("q b a c e", Intervals.phrase(t("q"), o, t("e"))));
    assertEquals(1, count("q b a c e", Intervals.phrase(t("q"), Intervals.or(o, NOWHERE), t("e"))));
  }

  public void testNestedMaxWidthInsideOr() throws IOException {
    // the query of TestDisjunctionRewrites#testNestedMaxWidth, plus one outer alternative
    IntervalsSource w =
        Intervals.maxwidth(
            4, Intervals.or(Intervals.ordered(t("b"), t("c")), Intervals.ordered(t("d"), t("e"))));
    assertEquals(1, count("a b d e c f", Intervals.phrase(t("a"), w, t("f"))));
    assertEquals(
        1, count("a b d e c f", Intervals.phrase(t("a"), Intervals.or(w, NOWHERE), t("f"))));
  }

  public void testControls() throws IOException {
    // a directly nested or() is flattened and rewritten
    assertEquals(
        1, count("x b c d", Intervals.phrase(t("x"), Intervals.or(bcOrB(), NOWHERE), t("d"))));
    // or(false, ...) is the documented opt-out: it may miss hits, and does here
    IntervalsSource w = Intervals.maxwidth(5, bcOrB());
    assertEquals(
        0, count("x b c d", Intervals.phrase(t("x"), Intervals.or(false, w, NOWHERE), t("d"))));
  }
}
