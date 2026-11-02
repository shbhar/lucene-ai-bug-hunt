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
package org.apache.lucene.queries.function;

import org.apache.lucene.index.VectorSimilarityFunction;
import org.apache.lucene.queries.function.valuesource.ConstKnnFloatValueSource;
import org.apache.lucene.queries.function.valuesource.FloatKnnVectorFieldSource;
import org.apache.lucene.queries.function.valuesource.FloatVectorSimilarityFunction;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestVectorSimilarityFunctionEquals extends LuceneTestCase {

  public void testSimilarityFunctionIsPartOfEquals() {
    ValueSource field = new FloatKnnVectorFieldSource("v");
    ValueSource target = new ConstKnnFloatValueSource(new float[] {1f, 2f});
    // Expected: EUCLIDEAN and COSINE sources are not equal; actual: equals() returns true.
    assertNotEquals(
        new FloatVectorSimilarityFunction(VectorSimilarityFunction.EUCLIDEAN, field, target),
        new FloatVectorSimilarityFunction(VectorSimilarityFunction.COSINE, field, target));
  }
}
