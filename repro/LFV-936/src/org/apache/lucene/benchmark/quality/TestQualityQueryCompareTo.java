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
package org.apache.lucene.benchmark.quality;

import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestQualityQueryCompareTo extends LuceneTestCase {

  public void testCompareToIsTransitive() {
    QualityQuery a = new QualityQuery("2", Map.of());
    QualityQuery b = new QualityQuery("100", Map.of());
    QualityQuery c = new QualityQuery("1a", Map.of());
    int ab = Integer.signum(a.compareTo(b));
    int bc = Integer.signum(b.compareTo(c));
    int ca = Integer.signum(c.compareTo(a));
    // expected a total order; actual 2 < 100 (as ints), 100 < 1a and 1a < 2 (as strings): a cycle
    assertFalse(
        "expected no cycle, got compareTo(2,100)=" + ab + ", (100,1a)=" + bc + ", (1a,2)=" + ca,
        ab != 0 && ab == bc && bc == ca);
  }
}
