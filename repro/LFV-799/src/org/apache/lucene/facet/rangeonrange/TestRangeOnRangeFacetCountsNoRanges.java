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
package org.apache.lucene.facet.rangeonrange;

import org.apache.lucene.document.RangeFieldQuery;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRangeOnRangeFacetCountsNoRanges extends LuceneTestCase {

  public void testNoRanges() throws Exception {
    FacetResult result;
    try {
      result =
          new LongRangeOnRangeFacetCounts(
                  "field", new FacetsCollector(), RangeFieldQuery.QueryType.INTERSECTS)
              .getAllChildren("field");
    } catch (IllegalArgumentException acceptable) {
      return;
    } catch (ArrayIndexOutOfBoundsException e) {
      throw new AssertionError(
          "expected an empty result or IllegalArgumentException, got " + e, e);
    }
    assertEquals(0, result.childCount);
  }
}
