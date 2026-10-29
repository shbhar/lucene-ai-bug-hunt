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
package org.apache.lucene.search;

import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSearchZeroDocLeaf extends LuceneTestCase {

  public void testCountOnEmptyParallelLeafReader() throws Exception {
    try (ParallelLeafReader empty = new ParallelLeafReader(true)) {
      int count;
      try {
        count = new IndexSearcher(empty).count(new MatchAllDocsQuery());
      } catch (IllegalArgumentException e) {
        // expected: count 0 on a reader whose only leaf has maxDoc() == 0; actual: it throws
        throw new AssertionError("expected count 0 for a maxDoc() == 0 leaf, but threw " + e, e);
      }
      assertEquals(0, count);
    }
  }
}
