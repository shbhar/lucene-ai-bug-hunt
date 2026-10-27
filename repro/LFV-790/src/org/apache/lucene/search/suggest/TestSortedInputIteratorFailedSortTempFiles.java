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
package org.apache.lucene.search.suggest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Set;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.search.suggest.fst.FSTCompletionLookup;
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * When the source, the per-entry encoding or the sort fails inside the SortedInputIterator
 * constructor, its temporary input file stays in the temp directory. WFSTCompletionLookup and
 * TSTLookup build through SortedInputIterator, so every failed build leaves one more file.
 * AnalyzingSuggester and FSTCompletionLookup delete their temp files on the same failures.
 */
public class TestSortedInputIteratorFailedSortTempFiles extends LuceneTestCase {

  private static FileDictionary dict(String content) {
    return new FileDictionary(
        new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)));
  }

  /** a source whose second next() fails, like a dictionary whose reader fails mid-way */
  private static InputIterator failingSource() {
    return new InputIterator() {
      int i;

      @Override
      public BytesRef next() throws IOException {
        if (i++ == 1) {
          throw new IOException("simulated read failure");
        }
        return new BytesRef("foo");
      }

      @Override
      public long weight() {
        return 1;
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

  public void testSortedInputIteratorConstructor() throws Exception {
    try (Directory tempDir = newDirectory()) {
      expectThrows(IOException.class, () -> new SortedInputIterator(tempDir, "suggest", failingSource()));
      // on main: [suggest_input_0.tmp]
      assertEquals("temp files after a failed sort", "[]", Arrays.toString(tempDir.listAll()));
    }
  }

  public void testWFSTCompletionLookupWeightOutOfRange() throws Exception {
    try (Directory tempDir = newDirectory()) {
      WFSTCompletionLookup lookup = new WFSTCompletionLookup(tempDir, "suggest");
      for (int i = 0; i < 3; i++) {
        // WFSTCompletionLookup accepts weights in [0, Integer.MAX_VALUE] only
        expectThrows(UnsupportedOperationException.class, () -> lookup.build(dict("foo\t3\nbar\t-1\n")));
      }
      // on main: three files, one per failed build
      assertEquals("temp files after failed builds", "[]", Arrays.toString(tempDir.listAll()));
    }
  }

  public void testTSTLookupSourceReadFailure() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(tempDir, "suggest");
      expectThrows(IOException.class, () -> lookup.build(failingSource()));
      assertEquals("temp files after a failed build", "[]", Arrays.toString(tempDir.listAll()));
    }
  }

  /** the two suggesters that sort without SortedInputIterator clean up on the same failure */
  public void testControlOtherSuggestersCleanUp() throws Exception {
    try (Directory tempDir = newDirectory(); MockAnalyzer a = new MockAnalyzer(random())) {
      AnalyzingSuggester analyzing = new AnalyzingSuggester(tempDir, "suggest", a);
      expectThrows(IOException.class, () -> analyzing.build(failingSource()));
      FSTCompletionLookup fst = new FSTCompletionLookup(tempDir, "suggest");
      expectThrows(IOException.class, () -> fst.build(failingSource()));
      assertEquals("[]", Arrays.toString(tempDir.listAll()));
    }
  }
}
