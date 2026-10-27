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
package org.apache.lucene.codecs.bloom;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/** 9.5-9.12 variant: the exact call DefaultBloomFilterFactory makes (createOptimalSet(maxDoc, 0.1023f)). */
public class TestOptimalSetLargeSegment extends LuceneTestCase {
  static int rejected(FuzzySet set) throws java.io.IOException {
    for (int i = 0; i < 1000; i++) set.addValue(new BytesRef("id" + i));
    int no = 0;
    for (int i = 0; i < 1000; i++) {
      if (set.contains(new BytesRef("absent" + i)) == FuzzySet.ContainsResult.NO) no++;
    }
    return no;
  }

  public void testLargeSegment() throws Exception {
    FuzzySet set = FuzzySet.createOptimalSet(226_279_579, 0.1023f);
    int no = rejected(set);
    assertTrue("rejected " + no + "/1000; filter=" + set, no > 500);
  }

  public void testControlOneDocLess() throws Exception {
    FuzzySet set = FuzzySet.createOptimalSet(226_279_578, 0.1023f);
    int no = rejected(set);
    assertTrue("rejected " + no + "/1000; filter=" + set, no > 500);
  }
}
