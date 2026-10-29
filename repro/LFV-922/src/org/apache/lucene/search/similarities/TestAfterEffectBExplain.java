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
package org.apache.lucene.search.similarities;

import org.apache.lucene.search.Explanation;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAfterEffectBExplain extends LuceneTestCase {

  public void testPlusOneChildren() {
    BasicStats stats = new BasicStats("field", 1);
    stats.setTotalTermFreq(6);
    stats.setDocFreq(3);
    Explanation[] children = new AfterEffectB().explain(stats, 1).getDetails();
    // children "F, ... + 1" and "n, ... + 1": expected 6 + 1 and 3 + 1, actual the raw 6 and 3
    assertEquals("F=7, n=4", "F=" + children[1].getValue() + ", n=" + children[2].getValue());
  }
}
