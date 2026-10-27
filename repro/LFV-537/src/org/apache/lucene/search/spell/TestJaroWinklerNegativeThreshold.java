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
package org.apache.lucene.search.spell;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JaroWinklerDistance.setThreshold: "Set to a negative value to get the Jaro distance." The bonus
 * is skipped only when the Jaro similarity is below the threshold, so a negative threshold applies
 * the Winkler bonus to every pair instead; only a threshold above 1 gives the Jaro distance.
 */
public class TestJaroWinklerNegativeThreshold extends LuceneTestCase {

  public void testNegativeThresholdGivesJaro() {
    // martha / marhta: 6 matches, 1 transposition -> Jaro = (6/6 + 6/6 + 5/6) / 3 = 0.9444...
    float jaro = (1f + 1f + 5f / 6f) / 3f;
    JaroWinklerDistance jw = new JaroWinklerDistance();
    float jaroWinkler = jw.getDistance("martha", "marhta"); // with the Winkler bonus: 0.9611
    assertTrue(jaroWinkler > jaro + 0.01f);

    JaroWinklerDistance above1 = new JaroWinklerDistance();
    above1.setThreshold(2f); // control: a threshold above 1 does disable the bonus
    assertEquals(jaro, above1.getDistance("martha", "marhta"), 1e-6f);

    jw.setThreshold(-1f);
    assertEquals(
        "setThreshold(-1) should give the Jaro distance", jaro, jw.getDistance("martha", "marhta"), 1e-6f);
  }
}
