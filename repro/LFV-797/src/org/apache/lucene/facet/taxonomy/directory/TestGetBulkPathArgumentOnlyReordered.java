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
package org.apache.lucene.facet.taxonomy.directory;

import java.util.Arrays;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestGetBulkPathArgumentOnlyReordered extends LuceneTestCase {

  public void testArgumentIsOnlyReordered() throws Exception {
    try (Directory dir = newDirectory()) {
      try (DirectoryTaxonomyWriter w = new DirectoryTaxonomyWriter(dir)) {
        w.addCategory(new FacetLabel("a"));
        w.addCategory(new FacetLabel("b"));
      }
      try (DirectoryTaxonomyReader r = new DirectoryTaxonomyReader(dir)) {
        r.getPath(2);
        // ordinal 2 is cached: expected {2, 1} in some order afterwards, actual [1, 1]
        int[] ordinals = {2, 1};
        r.getBulkPath(ordinals);
        int[] sorted = ordinals.clone();
        Arrays.sort(sorted);
        assertArrayEquals(
            "javadoc: getBulkPath may only reorder its parameter [2, 1], but it now holds "
                + Arrays.toString(ordinals),
            new int[] {1, 2},
            sorted);
      }
    }
  }
}
