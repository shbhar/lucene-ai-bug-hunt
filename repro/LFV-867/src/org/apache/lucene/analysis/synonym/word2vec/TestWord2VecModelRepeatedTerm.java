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
package org.apache.lucene.analysis.synonym.word2vec;

import java.util.Arrays;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

public class TestWord2VecModelRepeatedTerm extends LuceneTestCase {

  public void testRepeatedTermKeepsLaterVectors() {
    Word2VecModel model = new Word2VecModel(3, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
    try {
      model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
    } catch (IllegalArgumentException rejectingTheRepeatIsAlsoCorrect) {
      return;
    }
    model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {0, 1}));
    float[] b = model.vectorValue(new BytesRef("b"));
    // expected b's own vector [0.0, 1.0]; actual a's vector [1.0, 0.0]
    assertTrue(
        "expected b's vector [0.0, 1.0], actual " + Arrays.toString(b),
        Arrays.equals(new float[] {0, 1}, b));
  }
}
