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

import java.io.StringReader;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** FSTCompletionLookup with exactMatchFirst=false must rank an exact match by its weight. */
public class TestFSTCompletionLookupExactMatchFirst extends LuceneTestCase {
  // key TAB weight; with 10 buckets: star -> bucket 0, start -> bucket 3, star wars -> bucket 6
  private static final String DICT = "star\t1\nstar wars\t100\nstart\t50\n";
  // "Otherwise they appear in the order of discretized weight and alphabetical within the bucket."
  private static final String BY_WEIGHT = "[star wars/6, start/3, star/0]";

  public void testBuild() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "fst", 10, false);
      lookup.build(new FileDictionary(new StringReader(DICT)));
      assertEquals(BY_WEIGHT, lookup.lookup("star", true, 3).toString());
    }
  }

  public void testLoad() throws Exception {
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup built = new FSTCompletionLookup(dir, "fst", 10, false);
      built.build(new FileDictionary(new StringReader(DICT)));
      ByteBuffersDataOutput out = new ByteBuffersDataOutput();
      assertTrue(built.store(out));
      FSTCompletionLookup loaded = new FSTCompletionLookup(dir, "fst", 10, false);
      assertTrue(loaded.load(out.toDataInput()));
      assertEquals(BY_WEIGHT, loaded.lookup("star", true, 3).toString());
    }
  }

  /** Control: the constructor that takes a pre-built FSTCompletion honours the flag. */
  public void testPrebuiltCompletionControl() throws Exception {
    FSTCompletionBuilder builder = new FSTCompletionBuilder();
    builder.add(new BytesRef("star"), 0);
    builder.add(new BytesRef("star wars"), 6);
    builder.add(new BytesRef("start"), 3);
    try (Directory dir = newDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "fst", builder.build(), false);
      assertEquals(BY_WEIGHT, lookup.lookup("star", true, 3).toString());
    }
  }
}
