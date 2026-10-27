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

import java.io.IOException;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A TSTLookup loaded from its stored copy must answer like the original, also for keys with
 * supplementary characters. TSTLookup keeps one tree node per UTF-16 unit, so such a character is a
 * high-surrogate node followed by a low-surrogate node.
 */
public class TestTSTLookupSurrogatePersistence extends LuceneTestCase {

  private static final String CJK = "\uD842\uDFB7\u91CE\u5BB6"; // U+20BB7 (CJK Ext. B) + 2 chars
  private static final String EMOJI = "\uD83D\uDE00 smile"; // U+1F600, an emoji

  private static TSTLookup build(Directory dir, Input... inputs) throws IOException {
    TSTLookup lookup = new TSTLookup(dir, "tst");
    lookup.build(new InputArrayIterator(inputs));
    return lookup;
  }

  private static TSTLookup storeAndLoad(Directory dir, TSTLookup lookup) throws IOException {
    ByteBuffersDataOutput out = new ByteBuffersDataOutput();
    assertTrue(lookup.store(out));
    TSTLookup loaded = new TSTLookup(dir, "tst");
    assertTrue(loaded.load(out.toDataInput()));
    return loaded;
  }

  public void testSupplementaryKeysAfterStoreLoad() throws Exception {
    try (Directory dir = newDirectory()) {
      TSTLookup lookup = build(dir, new Input(CJK, 5), new Input(EMOJI, 4), new Input("apple", 3));
      assertEquals("[" + CJK + "/5]", lookup.lookup("\uD842\uDFB7", true, 5).toString());
      assertEquals("[" + EMOJI + "/4]", lookup.lookup("\uD83D\uDE00", true, 5).toString());
      assertEquals(5L, lookup.get(CJK));

      TSTLookup loaded = storeAndLoad(dir, lookup);
      assertEquals(3, loaded.getCount()); // controls: these survive
      assertEquals("[apple/3]", loaded.lookup("ap", true, 5).toString());
      assertEquals("[" + CJK + "/5]", loaded.lookup("\uD842\uDFB7", true, 5).toString());
      assertEquals("[" + EMOJI + "/4]", loaded.lookup("\uD83D\uDE00", true, 5).toString());
      assertEquals(5L, loaded.get(CJK));
    }
  }

  public void testNeighbouringKeysAfterStoreLoad() throws Exception {
    String fullwidth = "\uFF21b"; // FULLWIDTH LATIN CAPITAL LETTER A, then 'b'
    try (Directory dir = newDirectory()) {
      TSTLookup lookup =
          build(dir, new Input(EMOJI, 5), new Input(fullwidth, 4), new Input("ab", 3));
      assertEquals("[" + fullwidth + "/4]", lookup.lookup("\uFF21", true, 5).toString());
      assertEquals("[]", lookup.lookup("\uFFFD", true, 5).toString());

      TSTLookup loaded = storeAndLoad(dir, lookup);
      assertEquals("[ab/3]", loaded.lookup("a", true, 5).toString()); // control
      assertEquals("[" + fullwidth + "/4]", loaded.lookup("\uFF21", true, 5).toString());
      assertEquals("[]", loaded.lookup("\uFFFD", true, 5).toString());
    }
  }
}
