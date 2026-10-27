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
package org.apache.lucene.search.suggest.analyzing;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * With repeated copies in the input, maxSurfaceFormsPerAnalyzedForm still caps the number of
 * distinct surface forms kept per analyzed form, and keeps the highest-weighted ones.
 */
public class TestAnalyzingSuggesterSurfaceFormLimit extends LuceneTestCase {

  private static String lines(String text, long weight, int copies) {
    return (text + "\t" + weight + "\n").repeat(copies);
  }

  private List<String> suggest(int limit, String dictionary, String key, int num) throws Exception {
    try (Directory tempDir = newDirectory();
        Analyzer a = new StandardAnalyzer()) {
      int options = AnalyzingSuggester.EXACT_FIRST | AnalyzingSuggester.PRESERVE_SEP;
      AnalyzingSuggester s =
          new AnalyzingSuggester(tempDir, "suggest", a, a, options, limit, -1, true);
      s.build(new FileDictionary(new StringReader(dictionary)));
      List<String> out = new ArrayList<>();
      for (LookupResult r : s.lookup(key, false, num)) {
        out.add(r.key + "/" + r.value);
      }
      return out;
    }
  }

  /** Limit 2, 3 distinct forms (one repeated): the 2 highest-weighted distinct forms are kept. */
  public void testLimitStillCapsDistinctForms() throws Exception {
    String dict = lines("Apple", 10, 3) + lines("apple", 5, 1) + lines("APPLE", 3, 1);
    assertEquals(List.of("Apple/10", "apple/5"), suggest(2, dict, "app", 5));
  }

  /**
   * Limit 256, 300 copies of "applesauce" plus 400 distinct case variants with weights 1..400:
   * exactly 256 distinct forms are kept, "applesauce" and the 255 highest-weighted variants.
   */
  public void testDefaultLimitKeeps256DistinctForms() throws Exception {
    StringBuilder dict = new StringBuilder(lines("applesauce", 1000, 300));
    List<String> expected = new ArrayList<>();
    expected.add("applesauce/1000");
    for (int mask = 1; mask <= 400; mask++) {
      dict.append(lines(variant("applesauce", mask), mask, 1));
    }
    for (int mask = 400; mask > 400 - 255; mask--) {
      expected.add(variant("applesauce", mask) + "/" + mask);
    }
    assertEquals(expected, suggest(256, dict.toString(), "applesauce", 400));
  }

  /** Upper-cases the letters of word whose bit is set in mask. */
  private static String variant(String word, int mask) {
    StringBuilder sb = new StringBuilder(word);
    for (int i = 0; i < word.length(); i++) {
      if ((mask & (1 << i)) != 0) {
        sb.setCharAt(i, Character.toUpperCase(word.charAt(i)));
      }
    }
    return sb.toString();
  }
}
