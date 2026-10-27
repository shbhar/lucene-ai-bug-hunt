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

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * AnalyzingSuggester marks the end of an analyzed form with a 0x00 byte (END_BYTE) followed by a
 * dedup byte, which keeps FST inputs prefix-free only while analyzed forms contain no 0x00 byte.
 * LUCENE-4534 (CHANGES 4.1) made the suggester "allow 0 byte values in the lookup keys"; with
 * KeywordAnalyzer the analyzed form is the UTF-8 of the text, so a U+0000 in a key or a surface
 * form is such a 0x00 byte.
 */
public class TestAnalyzingSuggesterZeroByte extends LuceneTestCase {

  private static InputIterator inputs(String... values) {
    Iterator<String> it = Arrays.asList(values).iterator();
    long[] weight = {0};
    return new InputIterator() {
      @Override
      public BytesRef next() {
        weight[0]++;
        return it.hasNext() ? new BytesRef(it.next()) : null;
      }

      @Override
      public long weight() {
        return weight[0]; // 1, 2, ... in input order
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }

  private AnalyzingSuggester build(Directory dir, Analyzer a, int options, int maxSurface, String... v)
      throws Exception {
    AnalyzingSuggester s = new AnalyzingSuggester(dir, "suggest", a, a, options, maxSurface, -1, true);
    s.build(inputs(v));
    return s;
  }

  /** No entry starts with "b\u0000", so the prefix lookup must return nothing. */
  public void testKeyEndingWithZeroByteMatchesShorterEntry() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new KeywordAnalyzer()) {
      AnalyzingSuggester s = build(dir, a, 0, 256, "a", "ab", "b");
      assertEquals("[]", s.lookup("b\u0001", false, 10).toString()); // control
      assertEquals("[]", s.lookup("b\u0000", false, 10).toString());
    }
  }

  /** Entries "a" and "a\u0000": lookup("a") must list each entry once. */
  public void testExactFirstDuplicatesEntry() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new KeywordAnalyzer()) {
      AnalyzingSuggester s = build(dir, a, AnalyzingSuggester.EXACT_FIRST, 256, "a", "a\u0000");
      assertEquals("[a/1, a\u0000/2]", s.lookup("a", false, 5).toString());
    }
  }

  /** EXACT_FIRST must return the exact match "a" first, whatever maxSurfaceFormsPerAnalyzedForm. */
  public void testExactFirstMissesExactMatch() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new KeywordAnalyzer()) {
      AnalyzingSuggester s = build(dir, a, AnalyzingSuggester.EXACT_FIRST, 1, "a", "a\u0000");
      assertEquals("[a/1]", s.lookup("a", false, 1).toString());
    }
  }

  /** Control: the same dictionaries without U+0000 behave as documented. */
  public void testControlWithoutZeroByte() throws Exception {
    try (Directory dir = newDirectory();
        Analyzer a = new KeywordAnalyzer()) {
      AnalyzingSuggester s = build(dir, a, AnalyzingSuggester.EXACT_FIRST, 1, "a", "a\u0001");
      assertEquals("[a/1]", s.lookup("a", false, 1).toString());
      assertEquals("[a/1, a\u0001/2]", s.lookup("a", false, 5).toString());
      assertEquals("[]", s.lookup("a\u0002", false, 5).toString());
    }
  }
}
