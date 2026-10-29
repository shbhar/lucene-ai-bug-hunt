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
package org.apache.lucene.misc;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSweetSpotHyperbolicTfFarBelowMidpoint extends LuceneTestCase {

  public void testFarBelowMidpointGivesMin() {
    SweetSpotSimilarity sim = new SweetSpotSimilarity();
    sim.setHyperbolicTfFactors(0f, 2f, 10, 400f);
    // javadoc formula: 0 + (2-0)/2 * (tanh((1-400)*ln 10) + 1) = 0 (min); actual 2.0 (max)
    assertEquals("hyperbolicTf(1), factors (0, 2, 10, 400)", 0f, sim.hyperbolicTf(1), 1e-6f);
  }
}
