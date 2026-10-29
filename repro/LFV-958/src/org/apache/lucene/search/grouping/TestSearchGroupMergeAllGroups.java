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
package org.apache.lucene.search.grouping;

import java.util.Collection;
import java.util.List;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSearchGroupMergeAllGroups extends LuceneTestCase {

  private static SearchGroup<String> group(String value, int sortValue) {
    SearchGroup<String> group = new SearchGroup<>();
    group.groupValue = value;
    group.sortValues = new Object[] {sortValue};
    return group;
  }

  public void testMergeWithTopNMaxValue() {
    List<Collection<SearchGroup<String>>> shards = List.of(List.of(group("a", 1)), List.of(group("b", 2)));
    Sort sort = new Sort(new SortField("p", SortField.Type.INT));
    try {
      assertEquals(2, SearchGroup.merge(shards, 0, Integer.MAX_VALUE, sort).size());
    } catch (OutOfMemoryError e) {
      // expected: both groups, as with topN = 10; actual: the result list is pre-sized by topN
      throw new AssertionError("expected the 2 merged groups for topN = Integer.MAX_VALUE, but got " + e, e);
    }
  }
}
