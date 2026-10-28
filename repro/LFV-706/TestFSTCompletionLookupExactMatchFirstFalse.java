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
package org.apache.lucene.search.suggest.fst;

import java.util.Iterator;
import java.util.Set;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * FSTCompletionLookup(tempDir, prefix, buckets, exactMatchFirst) documents: "If true exact matches
 * are promoted to the top of the suggestions list. Otherwise they appear in the order of discretized
 * weight and alphabetical within the bucket." With exactMatchFirst=false and higher-weights-first
 * lookups (onlyMorePopular=true), the exact match is still promoted after build() and after load().
 */
public class TestFSTCompletionLookupExactMatchFirstFalse extends LuceneTestCase {

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

  // 3 inputs, 10 buckets: weight 1 -> bucket 0, weight 50 -> bucket 3, weight 100 -> bucket 6
  private static final String[] KEYS = {"star", "star wars", "start"};
  private static final long[] WEIGHTS = {1, 100, 50};
  // order of discretized weight (descending), no promotion of the exact match "star"
  private static final String EXPECTED = "[star wars/6, start/3, star/0]";

  public void testBuildHonoursExactMatchFirstFalse() throws Exception {
    try (Directory tempDir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(tempDir, "fst", 10, false);
      lookup.build(iter(KEYS, WEIGHTS));
      assertEquals(EXPECTED, lookup.lookup("star", true, 3).toString());
    }
  }

  public void testLoadHonoursExactMatchFirstFalse() throws Exception {
    try (Directory tempDir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(tempDir, "fst", 10, false);
      lookup.build(iter(KEYS, WEIGHTS));
      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(lookup.store(out));
      FSTCompletionLookup loaded = new FSTCompletionLookup(tempDir, "fst", 10, false);
      assertTrue(loaded.load(out.toDataInput()));
      assertEquals(EXPECTED, loaded.lookup("star", true, 3).toString());
    }
  }

  /** Control: the constructor that takes a pre-built FSTCompletion honours exactMatchFirst=false. */
  public void testPrebuiltCompletionConstructorHonoursExactMatchFirstFalse() throws Exception {
    try (Directory tempDir = newDirectory()) {
      FSTCompletionBuilder builder = new FSTCompletionBuilder();
      builder.add(new BytesRef("star"), 0);
      builder.add(new BytesRef("star wars"), 6);
      builder.add(new BytesRef("start"), 3);
      FSTCompletionLookup lookup =
          new FSTCompletionLookup(tempDir, "fst", builder.build(), false);
      assertEquals(EXPECTED, lookup.lookup("star", true, 3).toString());
    }
  }
}
