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

import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.TermAndVector;

public class TestWord2VecMinAcceptedSimilarityCosine extends BaseTokenStreamTestCase {

  public void testTermBelowCosineThresholdIsNotASynonym() throws Exception {
    Word2VecModel model = new Word2VecModel(2, 2);
    model.addTermAndVector(new TermAndVector(new BytesRef("a"), new float[] {1, 0}));
    model.addTermAndVector(new TermAndVector(new BytesRef("b"), new float[] {1, 1}));
    Word2VecSynonymFilter filter =
        new Word2VecSynonymFilter(
            whitespaceMockTokenizer("a"), new Word2VecSynonymProvider(model), 10, 0.8f);
    // expected: b (cosine 0.71 < 0.8) is no synonym; actual: b added (score (1 + cos) / 2 = 0.85)
    assertTokenStreamContents(filter, new String[] {"a"});
  }
}
