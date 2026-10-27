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

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.search.suggest.tst.TSTLookup;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * TSTLookup store/load round trip with keys that contain supplementary characters (UTF-16
 * surrogate pairs). Uses only public API: FileDictionary to feed the lookup, Lookup.store/load
 * (both the stream and the DataOutput/DataInput variants) and Lookup.lookup / TSTLookup.get.
 *
 * <p>Same shape as the upstream TestPersistence (every built key must still be found after
 * reloading), with non-BMP keys.
 */
public class TestTSTStoreLoadSupplementaryV1 extends LuceneTestCase {

  // key \t weight ; U+1D11E MUSICAL SYMBOL G CLEF, U+1F355 SLICE OF PIZZA, U+1F41D HONEYBEE
  private static final String[] KEYS = {
    "apple", "\uD834\uDD1Eclef", "\uD83C\uDF55pizza", "b\uD83D\uDC1D", "zebra",
  };
  private static final long[] WEIGHTS = {2, 7, 3, 4, 1};

  private static String dictText() {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < KEYS.length; i++) {
      sb.append(KEYS[i]).append('\t').append(WEIGHTS[i]).append('\n');
    }
    return sb.toString();
  }

  private TSTLookup buildLookup(Directory tempDir) throws Exception {
    TSTLookup lookup = new TSTLookup(tempDir, "suggest");
    lookup.build(new FileDictionary(new StringReader(dictText())));
    return lookup;
  }

  private static String hex(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      sb.append(String.format("\\u%04X", (int) s.charAt(i)));
    }
    return sb.toString();
  }

  private static List<String> render(List<LookupResult> results) {
    List<String> out = new ArrayList<>();
    for (LookupResult r : results) {
      out.add(hex(r.key.toString()) + "/" + r.value);
    }
    return out;
  }

  /** Mirrors TestPersistence: every key that was built must be found after store + load. */
  public void testEveryBuiltKeyFoundAfterStreamStoreLoad() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup built = buildLookup(tempDir);

      // Control: before persisting, every key is found with its weight.
      for (int i = 0; i < KEYS.length; i++) {
        List<LookupResult> r = built.lookup(KEYS[i], false, 1);
        assertEquals("before store, key " + hex(KEYS[i]), 1, r.size());
        assertEquals(KEYS[i], r.get(0).key.toString());
        assertEquals(WEIGHTS[i], r.get(0).value);
        assertEquals(WEIGHTS[i], ((Number) built.get(KEYS[i])).longValue());
      }

      Path dir = createTempDir("tst-v1");
      Path file = dir.resolve("lookup.dat");
      assertTrue(built.store(Files.newOutputStream(file)));

      TSTLookup loaded = new TSTLookup();
      assertTrue(loaded.load(Files.newInputStream(file)));
      assertEquals(built.getCount(), loaded.getCount());

      List<String> missing = new ArrayList<>();
      for (int i = 0; i < KEYS.length; i++) {
        List<LookupResult> r = loaded.lookup(KEYS[i], false, 1);
        Object v = loaded.get(KEYS[i]);
        System.out.println(
            "after load: key="
                + hex(KEYS[i])
                + " lookup="
                + render(r)
                + " get="
                + v);
        if (r.size() != 1 || v == null) {
          missing.add(hex(KEYS[i]));
        }
      }
      assertTrue("keys not found after store+load: " + missing, missing.isEmpty());
    }
  }

  /**
   * A BMP-only key is also affected when it sits next to a surrogate node: here the emoji key is
   * the balanced tree's middle (root) and the half-width katakana key U+FF76 U+FF9E is in its
   * high subtree. After reload the root split char is no longer U+D83D, so the search for U+FF76
   * goes to the low side and misses the key.
   */
  public void testBmpKeyBesideSurrogateNodeAfterRoundTrip() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup built = new TSTLookup(tempDir, "suggest");
      // sorted (UTF-16 order): "ok" < U+1F44D THUMBS UP < U+FF76 U+FF9E
      built.build(
          new FileDictionary(
              new StringReader("ok\t1\n\uD83D\uDC4D\t2\n\uFF76\uFF9E\t3\n")));
      String bmpKey = "\uFF76\uFF9E";
      assertEquals(List.of(hex(bmpKey) + "/3"), render(built.lookup(bmpKey, false, 10)));

      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(built.store(out));
      TSTLookup loaded = new TSTLookup();
      assertTrue(loaded.load(out.toDataInput()));

      List<String> after = render(loaded.lookup(bmpKey, false, 10));
      System.out.println("bmp key after load: lookup=" + after + " get=" + loaded.get(bmpKey));
      assertEquals(
          "BMP key U+FF76 U+FF9E after store+load", List.of(hex(bmpKey) + "/3"), after);
    }
  }

  /**
   * Differential: every prefix (cut at code-point boundaries) of every key gives the same
   * suggestions before and after a DataOutput/DataInput round trip.
   */
  public void testPrefixLookupsUnchangedAfterDataOutputRoundTrip() throws Exception {
    try (Directory tempDir = newDirectory()) {
      TSTLookup built = buildLookup(tempDir);

      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(built.store(out));
      TSTLookup loaded = new TSTLookup();
      assertTrue(loaded.load(out.toDataInput()));

      TreeSet<String> prefixes = new TreeSet<>();
      for (String k : KEYS) {
        for (int i = 1; i <= k.length(); i++) {
          if (i < k.length() && Character.isLowSurrogate(k.charAt(i))) {
            continue; // never cut a surrogate pair
          }
          prefixes.add(k.substring(0, i));
        }
      }
      prefixes.add("\uFFFD"); // a prefix that no key starts with

      List<String> diffs = new ArrayList<>();
      for (String p : prefixes) {
        List<String> before = render(built.lookup(p, false, 100));
        List<String> after = render(loaded.lookup(p, false, 100));
        before.sort(null);
        after.sort(null);
        if (!before.equals(after)) {
          diffs.add("prefix=" + hex(p) + " before=" + before + " after=" + after);
        }
      }
      for (String d : diffs) {
        System.out.println("DIFF " + d);
      }
      System.out.println("prefixes checked=" + prefixes.size() + " differing=" + diffs.size());
      assertTrue("prefix lookups changed by store+load: " + diffs, diffs.isEmpty());
    }
  }
}
