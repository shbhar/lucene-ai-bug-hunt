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
package org.apache.lucene.search.spell;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Metamorphic check (independent of the candidate's own test): setDistance says candidates are drawn
 * from the term dictionary by Damerau-Levenshtein and then ranked by the configured metric. A
 * strictly increasing transform of the internal similarity ranks every candidate pool exactly as the
 * internal distance does (same order, same ties), so with accuracy 0 the suggestions must be the same
 * strings in the same order. A separate LuceneLevenshteinDistance instance (identity transform, but
 * not == INTERNAL_LEVENSHTEIN) is the control.
 */
public class TestDscMonotoneDistanceInvariance extends LuceneTestCase {

  /** sqrt of the internal-equivalent similarity: strictly increasing, stays in [0, 1]. */
  static final class SqrtLev implements StringDistance {
    final LuceneLevenshteinDistance d = new LuceneLevenshteinDistance();

    @Override
    public float getDistance(String s1, String s2) {
      return (float) Math.sqrt(d.getDistance(s1, s2));
    }
  }

  private static IndexReader index(Directory dir, List<String> terms) throws Exception {
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    for (String t : terms) {
      Document doc = new Document();
      doc.add(new StringField("f", t, Field.Store.NO));
      w.addDocument(doc);
    }
    w.commit();
    w.close();
    return DirectoryReader.open(dir);
  }

  private static List<String> run(
      IndexReader r, StringDistance sd, String q, int numSug, int maxInspections) throws Exception {
    DirectSpellChecker sc = new DirectSpellChecker();
    if (sd != null) {
      sc.setDistance(sd);
    }
    sc.setMaxInspections(maxInspections);
    SuggestWord[] s = sc.suggestSimilar(new Term("f", q), numSug, r, SuggestMode.SUGGEST_ALWAYS, 0f);
    List<String> out = new ArrayList<>();
    for (SuggestWord w : s) {
      out.add(w.string);
    }
    return out;
  }

  /** Hand-built case: index {abc, abcde}, query abcd, numSug 1, maxInspections 1. */
  public void testSmallCase() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexReader r = index(dir, Arrays.asList("abc", "abcde"))) {
      assertEquals(List.of("abcde"), run(r, null, "abcd", 1, 1));
      assertEquals("control", List.of("abcde"), run(r, new LuceneLevenshteinDistance(), "abcd", 1, 1));
      assertEquals("sqrt", List.of("abcde"), run(r, new SqrtLev(), "abcd", 1, 1));
    }
  }

  /** Same check on default settings (maxInspections 5) over random small indexes. */
  public void testRandomIndexes() throws Exception {
    Random rnd = new Random(42);
    int checks = 0, controlMismatches = 0, sqrtMismatches = 0, sqrtMismatchesDefaultInspections = 0;
    StringBuilder first = new StringBuilder();
    for (int iter = 0; iter < 400; iter++) {
      List<String> terms = new ArrayList<>();
      int n = 3 + rnd.nextInt(8);
      for (int i = 0; i < n; i++) {
        terms.add(randomWord(rnd, 2 + rnd.nextInt(4)));
      }
      try (Directory dir = new ByteBuffersDirectory();
          IndexReader r = index(dir, terms)) {
        for (int k = 0; k < 5; k++) {
          String q = randomWord(rnd, 3 + rnd.nextInt(3));
          for (int numSug : new int[] {1, 2}) {
            for (int mi : new int[] {1, 5}) {
              List<String> ref = run(r, null, q, numSug, mi);
              List<String> ctl = run(r, new LuceneLevenshteinDistance(), q, numSug, mi);
              List<String> sq = run(r, new SqrtLev(), q, numSug, mi);
              checks++;
              if (!ref.equals(ctl)) {
                controlMismatches++;
              }
              if (!ref.equals(sq)) {
                sqrtMismatches++;
                if (mi == 5) {
                  sqrtMismatchesDefaultInspections++;
                }
                if (first.length() < 600) {
                  first.append(
                      String.format(
                          "%n  terms=%s q=%s numSug=%d maxInspections=%d internal=%s sqrt=%s",
                          terms, q, numSug, mi, ref, sq));
                }
              }
            }
          }
        }
      }
    }
    String summary =
        "checks="
            + checks
            + " controlMismatches="
            + controlMismatches
            + " sqrtMismatches="
            + sqrtMismatches
            + " (with default maxInspections 5: "
            + sqrtMismatchesDefaultInspections
            + ")"
            + first;
    System.out.println(summary);
    assertEquals("control must match: " + summary, 0, controlMismatches);
    assertEquals(summary, 0, sqrtMismatches);
  }

  private static String randomWord(Random rnd, int len) {
    char[] c = new char[len];
    for (int i = 0; i < len; i++) {
      c[i] = (char) ('a' + rnd.nextInt(3));
    }
    return new String(c);
  }
}
