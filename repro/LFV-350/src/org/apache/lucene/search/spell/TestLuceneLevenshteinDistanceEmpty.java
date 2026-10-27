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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * StringDistance#getDistance: "Returns a float between 0 and 1 ... Returning a value of 1 means the
 * specified strings are identical". LuceneLevenshteinDistance is documented as "implemented in a
 * consistent way as Lucene's FuzzyTermsEnum", which scores an exact match 1.0 and a candidate at
 * edit distance ed as 1 - ed / min(length): -Infinity when one side is empty. For empty input the
 * class instead returns 0 for two empty strings and max(n, m) (e.g. 3.0) for one empty string.
 */
public class TestLuceneLevenshteinDistanceEmpty extends LuceneTestCase {

  public void testIdenticalEmptyStringsScoreOne() {
    StringDistance sd = new LuceneLevenshteinDistance();
    assertEquals(1.0f, sd.getDistance("abc", "abc"), 0f); // control: identical non-empty strings
    assertEquals(1.0f, new LevenshteinDistance().getDistance("", ""), 0f); // sibling metric
    assertEquals("identical (empty) strings", 1.0f, sd.getDistance("", ""), 0f);
  }

  public void testEmptyVsNonEmptyNotAboveIdentical() {
    StringDistance sd = new LuceneLevenshteinDistance();
    float identical = sd.getDistance("abc", "abc");
    float empty = sd.getDistance("", "abc");
    assertTrue(
        "getDistance(\"\", \"abc\") = " + empty + " must not exceed the identical score " + identical,
        empty <= identical);
    assertTrue("getDistance(\"abc\", \"\") = " + sd.getDistance("abc", ""), sd.getDistance("abc", "") <= 1.0f);
  }

  /**
   * In-tree effect: DirectSpellChecker scores candidates with the configured distance when it is not
   * the INTERNAL_LEVENSHTEIN instance. With a separate LuceneLevenshteinDistance instance the empty
   * term is suggested for "a" with score 1.0, while the internal scoring (FuzzyTermsEnum's boost,
   * -Infinity for the empty term) filters it out by the default accuracy 0.5.
   */
  public void testDirectSpellCheckerSuggestsEmptyTermWithPerfectScore() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        for (String s : new String[] {"", "b", "xyz"}) {
          Document doc = new Document();
          doc.add(new StringField("f", s, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        DirectSpellChecker internal = new DirectSpellChecker();
        internal.setMinPrefix(0);
        internal.setMinQueryLength(1);
        SuggestWord[] viaInternal = internal.suggestSimilar(new Term("f", "a"), 5, reader);

        DirectSpellChecker explicit = new DirectSpellChecker();
        explicit.setMinPrefix(0);
        explicit.setMinQueryLength(1);
        explicit.setDistance(new LuceneLevenshteinDistance());
        SuggestWord[] viaExplicit = explicit.suggestSimilar(new Term("f", "a"), 5, reader);

        assertEquals("internal scoring suggests nothing", 0, viaInternal.length);
        StringBuilder got = new StringBuilder();
        for (SuggestWord sw : viaExplicit) got.append("['").append(sw.string).append("' score=").append(sw.score).append("] ");
        assertEquals(
            "the same metric through LuceneLevenshteinDistance must agree with the internal one; got " + got,
            0,
            viaExplicit.length);
      }
    }
  }
}
