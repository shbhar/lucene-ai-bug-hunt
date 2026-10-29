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
import java.util.List;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFSTCompletionLookupNegativeWeights extends LuceneTestCase {

  public void testNegativeWeightRankedBelowPositive() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      FSTCompletionLookup lookup = new FSTCompletionLookup(dir, "fst");
      lookup.build(new FileDictionary(new StringReader("apple\t10\napricot\t-10\n")));
      List<LookupResult> results = lookup.lookup("ap", true, 2);
      // expected apple (weight 10) before apricot (weight -10)
      assertEquals("results " + results, "apple", results.get(0).key.toString());
    }
  }
}
