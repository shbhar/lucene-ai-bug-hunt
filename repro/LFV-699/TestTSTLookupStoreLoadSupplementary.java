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
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Lookup#store / Lookup#load must persist the lookup data: a TSTLookup loaded from a stored copy
 * must return the same suggestions. TSTLookup splits keys into UTF-16 chars, so a supplementary
 * character (here U+20BB7, and an emoji U+1F600) is stored as a high and a low surrogate split
 * char. store() writes each split char with DataOutput#writeString, which encodes a lone surrogate
 * as U+FFFD, so after load() the keys with supplementary characters can no longer be found.
 */
public class TestTSTLookupStoreLoadSupplementary extends LuceneTestCase {

  // U+20BB7 followed by two BMP CJK characters, and U+1F600 followed by " smile"
  private static final String CJK = "\uD842\uDFB7\u91CE\u5BB6";
  private static final String EMOJI = "\uD83D\uDE00 smile";

  private static InputIterator iter(String[] keys, long[] weights) {
    Iterator<Integer> it = java.util.stream.IntStream.range(0, keys.length).iterator();
    return new InputIterator() {
      int cur = -1;

      @Override
      public BytesRef next() {
        if (it.hasNext() == false) {
          return null;
        }
        cur = it.next();
        return new BytesRef(keys[cur]);
      }

      @Override
      public long weight() {
        return weights[cur];
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

  public void testStoreLoadKeepsSupplementaryCharacterSuggestions() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(tempDir, "tst");
      lookup.build(iter(new String[] {CJK, EMOJI, "apple"}, new long[] {5, 4, 3}));

      // before store/load: every key is found
      List<LookupResult> cjkBefore = lookup.lookup(CJK.substring(0, 2), true, 5);
      List<LookupResult> emojiBefore = lookup.lookup(EMOJI.substring(0, 2), true, 5);
      assertEquals("[" + CJK + "/5]", cjkBefore.toString());
      assertEquals("[" + EMOJI + "/4]", emojiBefore.toString());
      assertEquals(Long.valueOf(5), lookup.get(CJK));

      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(lookup.store(out));
      TSTLookup loaded = new TSTLookup(tempDir, "tst");
      assertTrue(loaded.load(out.toDataInput()));

      // control: count and an ASCII key survive the round trip
      assertEquals(lookup.getCount(), loaded.getCount());
      assertEquals("[apple/3]", loaded.lookup("ap", true, 5).toString());

      // the loaded copy must return the same suggestions
      assertEquals(cjkBefore.toString(), loaded.lookup(CJK.substring(0, 2), true, 5).toString());
      assertEquals(emojiBefore.toString(), loaded.lookup(EMOJI.substring(0, 2), true, 5).toString());
      assertEquals(Long.valueOf(5), loaded.get(CJK));
    }
  }

  /**
   * The U+FFFD split char also breaks the binary-search order of its tree level: a BMP key whose
   * first char sorts between the surrogate and U+FFFD (here fullwidth U+FF21) sits in the hiKid
   * subtree of the surrogate node and is no longer found either.
   */
  public void testStoreLoadKeepsBmpKeysNextToSurrogateNodes() throws Exception {
    String fullwidth = "\uFF21b"; // FULLWIDTH LATIN CAPITAL LETTER A, then 'b'
    try (Directory tempDir = newDirectory()) {
      TSTLookup lookup = new TSTLookup(tempDir, "tst");
      lookup.build(iter(new String[] {EMOJI, fullwidth, "ab"}, new long[] {5, 4, 3}));
      assertEquals("[" + fullwidth + "/4]", lookup.lookup("\uFF21", true, 5).toString());

      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(lookup.store(out));
      TSTLookup loaded = new TSTLookup(tempDir, "tst");
      assertTrue(loaded.load(out.toDataInput()));

      assertEquals("[ab/3]", loaded.lookup("a", true, 5).toString());
      assertEquals("[" + fullwidth + "/4]", loaded.lookup("\uFF21", true, 5).toString());
      assertEquals(Long.valueOf(4), loaded.get(fullwidth));
    }
  }
}
