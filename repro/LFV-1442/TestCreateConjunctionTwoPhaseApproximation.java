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
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCreateConjunctionTwoPhaseApproximation extends LuceneTestCase {

  public void testTwoPhaseIteratorIsIntersected() throws IOException {
    TwoPhaseIterator fourToSix =
        new TwoPhaseIterator(DocIdSetIterator.range(4, 7)) {
          @Override
          public boolean matches() {
            return true;
          }

          @Override
          public float matchCost() {
            return 1;
          }
        };
    DocIdSetIterator conjunction =
        ConjunctionUtils.createConjunction(
            new ArrayList<>(List.of(DocIdSetIterator.all(10))), new ArrayList<>(List.of(fourToSix)));
    List<Integer> docs = new ArrayList<>();
    for (int doc = conjunction.nextDoc(); doc != DocIdSetIterator.NO_MORE_DOCS; doc = conjunction.nextDoc()) {
      docs.add(doc);
    }
    // expected [4, 5, 6] (all(10) AND range(4, 7)); actual: all ten documents
    assertEquals(List.of(4, 5, 6), docs);
  }
}
