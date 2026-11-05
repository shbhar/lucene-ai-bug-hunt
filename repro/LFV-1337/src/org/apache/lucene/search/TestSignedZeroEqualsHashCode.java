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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.LatLonPoint;
import org.apache.lucene.document.XYDocValuesField;
import org.apache.lucene.index.Term;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSignedZeroEqualsHashCode extends LuceneTestCase {

  public void testEqualObjectsHaveEqualHashCodes() {
    Object[][] pairs = {
      {blended(0f), blended(-0f)},
      {
        LatLonPoint.newDistanceFeatureQuery("p", 1f, 0.0, 0.0, 1000.0),
        LatLonPoint.newDistanceFeatureQuery("p", 1f, -0.0, 0.0, 1000.0)
      },
      {XYDocValuesField.newDistanceSort("xy", 0f, 0f), XYDocValuesField.newDistanceSort("xy", -0f, 0f)}
    };
    List<String> broken = new ArrayList<>();
    for (Object[] p : pairs) {
      if (p[0].equals(p[1]) && p[0].hashCode() != p[1].hashCode()) {
        broken.add(p[0].getClass().getSimpleName());
      }
    }
    // Expected: equal objects (built with 0.0 and -0.0) hash equally; actual: all three pairs differ.
    assertEquals(List.of(), broken);
  }

  private static Query blended(float tieBreaker) {
    return new BlendedTermQuery.Builder()
        .add(new Term("f", "a"))
        .setRewriteMethod(new BlendedTermQuery.DisjunctionMaxRewrite(tieBreaker))
        .build();
  }
}
