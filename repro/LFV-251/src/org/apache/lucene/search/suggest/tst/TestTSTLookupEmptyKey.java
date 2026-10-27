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
package org.apache.lucene.search.suggest.tst;

import java.util.Iterator;
import java.util.List;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * TSTLookup.lookup with an empty key: TSTAutocomplete.prefixCompletion reads key.charAt(0)
 * before checking the key length and throws StringIndexOutOfBoundsException.
 */
public class TestTSTLookupEmptyKey extends LuceneTestCase {

  private static InputIterator iter(String[] s, long[] w) {
    Iterator<Integer> it = java.util.stream.IntStream.range(0, s.length).iterator();
    return new InputIterator() {
      int cur;

      @Override
      public BytesRef next() {
        if (it.hasNext() == false) {
          return null;
        }
        cur = it.next();
        return new BytesRef(s[cur]);
      }

      @Override
      public long weight() {
        return w[cur];
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

  public void testEmptyKey() throws Exception {
    Directory tempDir = newDirectory();
    TSTLookup lookup = new TSTLookup(tempDir, "suggest");
    lookup.build(iter(new String[] {"apple", "banana"}, new long[] {5, 3}));
    for (boolean onlyMorePopular : new boolean[] {false, true}) {
      // an empty key is a legal CharSequence (e.g. an empty search box); other Lookup
      // implementations answer it (AnalyzingSuggester documents "no results")
      List<LookupResult> results = lookup.lookup("", onlyMorePopular, 5);
      assertNotNull(results);
    }
    tempDir.close();
  }

  /** Control: non-empty keys work on the same lookup. */
  public void testNonEmptyKeyControl() throws Exception {
    Directory tempDir = newDirectory();
    TSTLookup lookup = new TSTLookup(tempDir, "suggest");
    lookup.build(iter(new String[] {"apple", "banana"}, new long[] {5, 3}));
    assertEquals("[apple/5]", lookup.lookup("a", true, 5).toString());
    tempDir.close();
  }
}
