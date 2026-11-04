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

import org.apache.lucene.queries.CommonTermsQuery;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queries.function.valuesource.DoubleConstValueSource;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.queries.function.valuesource.LinearFloatFunction;
import org.apache.lucene.queries.function.valuesource.RangeMapFloatFunction;
import org.apache.lucene.queries.function.valuesource.ReciprocalFloatFunction;
import org.apache.lucene.queries.function.valuesource.ScaleFloatFunction;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSignedZeroEqualsHashCode extends LuceneTestCase {

  public void testEqualObjectsHaveEqualHashCodes() {
    ValueSource n = new IntFieldSource("n");
    Object[][] pairs = {
      {new ConstValueSource(0f), new ConstValueSource(-0f)},
      {new DoubleConstValueSource(0.0), new DoubleConstValueSource(-0.0)},
      {new LinearFloatFunction(n, 0f, 1f), new LinearFloatFunction(n, -0f, 1f)},
      {new ScaleFloatFunction(n, 0f, 1f), new ScaleFloatFunction(n, -0f, 1f)},
      {new RangeMapFloatFunction(n, 0f, 1f, 5f, null), new RangeMapFloatFunction(n, -0f, 1f, 5f, null)},
      {new ReciprocalFloatFunction(n, 0f, 1f, 1f), new ReciprocalFloatFunction(n, -0f, 1f, 1f)},
      {commonTerms(0f), commonTerms(-0f)}
    };
    // Expected: equal objects hash equally; actual: built with 0.0 vs -0.0 they are equal but hash differently.
    for (Object[] p : pairs) {
      if (p[0].equals(p[1])) {
        assertEquals(p[0].getClass().getSimpleName(), p[0].hashCode(), p[1].hashCode());
      }
    }
  }

  private static CommonTermsQuery commonTerms(float lowFreqMinShouldMatch) {
    CommonTermsQuery q = new CommonTermsQuery(Occur.SHOULD, Occur.SHOULD, 0.5f);
    q.setLowFreqMinimumNumberShouldMatch(lowFreqMinShouldMatch);
    return q;
  }
}
