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
package org.apache.lucene.spatial.prefix.tree;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.spatial.prefix.tree.NumberRangePrefixTree.NRShape;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * NRShape#toString says "The result should be parseable by parseShape(String)", and parseShape
 * says its range syntax is "the same thing as the toString() of the range shape". For a date range
 * that ends with a year ending in 999 (e.g. the 1990s), range normalization turns the end into a
 * thousand-year cell and toString() prints that cell as its FIRST year, so the string is either
 * rejected by parseShape or parses to a much shorter range.
 */
public class TestDateRangeShapeToStringRoundTrip extends LuceneTestCase {

  public void testToStringParsesBackToSameShape() throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    List<String> wrong = new ArrayList<>();
    for (String s : new String[] {"[1990 TO 1999]", "[* TO 1999-12-31]", "[2014 TO 2999]"}) {
      NRShape shape = tree.parseShape(s);
      String str = shape.toString();
      try {
        NRShape reparsed = tree.parseShape(str);
        if (!reparsed.equals(shape)) {
          wrong.add(s + " -> toString \"" + str + "\" -> parses to a different shape " + reparsed);
        }
      } catch (IllegalArgumentException e) {
        wrong.add(s + " -> toString \"" + str + "\" -> parseShape throws: " + e.getMessage());
      }
    }
    assertTrue(String.join("; ", wrong), wrong.isEmpty());
  }
}
