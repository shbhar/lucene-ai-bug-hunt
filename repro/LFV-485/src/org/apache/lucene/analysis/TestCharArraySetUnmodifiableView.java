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
package org.apache.lucene.analysis;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CharArraySet.unmodifiableSet (and CharArrayMap.unmodifiableMap) are documented to "provide
 * unmodifiable views of internal sets for read-only use". The returned object shares the backing
 * set's hash table arrays but copies its element count, and it keeps the old arrays when the
 * backing set rehashes. Once the backing set changes, the "view" is neither a view nor a snapshot.
 */
@ThreadLeakScope(ThreadLeakScope.Scope.NONE) // the hanging lookup thread cannot be stopped
public class TestCharArraySetUnmodifiableView extends LuceneTestCase {

  /** The view's size() must equal the number of elements it iterates and contains. */
  public void testViewSizeMatchesItsElements() {
    CharArraySet backing = new CharArraySet(0, false);
    backing.add("a");
    CharArraySet view = CharArraySet.unmodifiableSet(backing);
    backing.add("b"); // no rehash: the new key lands in the shared table

    int iterated = 0;
    for (Object o : view) {
      iterated++;
    }
    assertTrue("the view sees the later addition", view.contains("b"));
    assertEquals("view.size() vs. elements the view iterates", iterated, view.size());
  }

  /**
   * A lookup of an absent key in the view must terminate. The backing set starts with the minimum
   * table of 8 slots; its 8th element fills that table before the rehash, and the view keeps the
   * full table, so probing for an absent key never finds an empty slot.
   */
  public void testLookupOfAbsentKeyTerminates() throws Exception {
    CharArraySet backing = new CharArraySet(0, false);
    backing.add("a");
    CharArraySet view = CharArraySet.unmodifiableSet(backing);
    for (int i = 0; i < 7; i++) {
      backing.add("x" + i);
    }
    assertEquals(8, backing.size());

    AtomicReference<Boolean> result = new AtomicReference<>();
    Thread t = new Thread(() -> result.set(view.contains("absent")), "view-lookup");
    t.setDaemon(true);
    t.start();
    t.join(10_000);
    assertFalse("view.contains(\"absent\") did not return within 10 s", t.isAlive());
    assertEquals(Boolean.FALSE, result.get());
  }

  /** Same hang through the analysis API: a StopFilter over the view never finishes a token. */
  public void testStopFilterOverViewTerminates() throws Exception {
    CharArraySet backing = new CharArraySet(0, true);
    backing.add("the");
    CharArraySet stopWords = CharArraySet.unmodifiableSet(backing);
    for (String w : new String[] {"a", "an", "and", "of", "or", "to", "in"}) {
      backing.add(w);
    }

    AtomicReference<String> first = new AtomicReference<>();
    Thread t =
        new Thread(
            () -> {
              try (Tokenizer tok = new org.apache.lucene.tests.analysis.MockTokenizer();
                  TokenStream ts = new StopFilter(tok, stopWords)) {
                tok.setReader(new java.io.StringReader("lucene"));
                org.apache.lucene.analysis.tokenattributes.CharTermAttribute term =
                    ts.addAttribute(
                        org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
                ts.reset();
                while (ts.incrementToken()) {
                  if (first.get() == null) {
                    first.set(term.toString());
                  }
                }
                ts.end();
              } catch (java.io.IOException e) {
                throw new java.io.UncheckedIOException(e);
              }
            },
            "stopfilter");
    t.setDaemon(true);
    t.start();
    t.join(10_000);
    assertFalse("StopFilter over the view did not emit 'lucene' within 10 s", t.isAlive());
    assertEquals("lucene", first.get());
  }
}
