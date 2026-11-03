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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestConjunctionTwoPhaseListOf extends LuceneTestCase {

  private static TwoPhaseIterator matchAll(float matchCost) {
    return new TwoPhaseIterator(DocIdSetIterator.all(1)) {
      @Override
      public boolean matches() {
        return true;
      }

      @Override
      public float matchCost() {
        return matchCost;
      }
    };
  }

  public void testImmutableTwoPhaseList() throws IOException {
    TwoPhaseIterator a = matchAll(2), b = matchAll(1);
    // expected: a conjunction matching doc 0; actual: UnsupportedOperationException (List.of is sorted in place)
    DocIdSetIterator conjunction =
        ConjunctionUtils.createConjunction(
            List.of(a.approximation(), b.approximation()), List.of(a, b));
    assertEquals(0, conjunction.nextDoc());
  }
}
