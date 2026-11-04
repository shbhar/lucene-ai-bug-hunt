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
package org.apache.lucene.index;

import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCheckImpactsAcrossLevels extends LuceneTestCase {

  public void testUndominatedImpactIsRejected() {
    Impacts impacts =
        new Impacts() {
          @Override
          public int numLevels() {
            return 2;
          }

          @Override
          public int getDocIdUpTo(int level) {
            return DocIdSetIterator.NO_MORE_DOCS;
          }

          @Override
          public FreqAndNormBuffer getImpacts(int level) {
            FreqAndNormBuffer buffer = new FreqAndNormBuffer();
            buffer.add(1, 1);
            if (level == 0) {
              buffer.add(5, 5);
            } else {
              buffer.add(2, 2);
            }
            return buffer;
          }
        };
    // No level-1 impact dominates (5,5): expected a CheckIndexException; actual: none is thrown.
    expectThrows(CheckIndex.CheckIndexException.class, () -> CheckIndex.checkImpacts(impacts, 0));
  }
}
