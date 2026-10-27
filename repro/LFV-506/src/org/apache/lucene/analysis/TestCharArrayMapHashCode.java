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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CharArraySet and CharArrayMap inherit hashCode() from AbstractSet/AbstractMap, which sum the
 * hash codes of the iterated elements/entries. The iterators hand out a fresh char[] clone per key
 * (CharArrayMap.MapEntry.getKey) and a fresh MapEntry per entry, neither of which has a content
 * based hashCode, so the result is a sum of identity hash codes of temporary objects. equals()
 * is content based (AbstractSet/AbstractMap.equals via contains/get), so the Object contract
 * breaks: hashCode() changes between calls on an unchanged set, and equal sets hash differently.
 */
public class TestCharArrayMapHashCode extends LuceneTestCase {

  public void testSetHashCodeIsStable() {
    CharArraySet set = new CharArraySet(List.of("foo", "bar"), false);
    int h1 = set.hashCode();
    int h2 = set.hashCode();
    assertEquals("hashCode() of an unchanged CharArraySet", h1, h2);
  }

  public void testEqualSetsHaveEqualHashCodes() {
    CharArraySet a = new CharArraySet(List.of("foo", "bar"), false);
    CharArraySet b = new CharArraySet(List.of("foo", "bar"), false);
    assertEquals(a, b);
    assertEquals("hashCode() of equal CharArraySets", a.hashCode(), b.hashCode());
  }

  public void testMapHashCodeIsStable() {
    CharArrayMap<Integer> map = new CharArrayMap<>(4, false);
    map.put("foo", 1);
    map.put("bar", 2);
    int h1 = map.hashCode();
    int h2 = map.hashCode();
    assertEquals("hashCode() of an unchanged CharArrayMap", h1, h2);
  }

  /** Consequence: a CharArraySet cannot be found in a HashSet it was added to. */
  public void testUsableAsHashKey() {
    CharArraySet stopWords = new CharArraySet(List.of("the", "a"), true);
    Set<Object> seen = new HashSet<>();
    seen.add(stopWords);
    assertTrue("HashSet.contains(the same CharArraySet)", seen.contains(stopWords));
  }
}
