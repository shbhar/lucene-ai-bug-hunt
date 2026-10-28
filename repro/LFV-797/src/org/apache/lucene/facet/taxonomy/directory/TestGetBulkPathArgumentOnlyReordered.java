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

/**
 * TaxonomyReader#getBulkPath javadoc: "this method may change (reorder elements) its parameter".
 * DirectoryTaxonomyReader#getBulkPath compacts the uncached ordinals into the front of the
 * argument array before sorting them, so when some ordinals are already in the category cache the
 * array no longer holds the same elements: values are overwritten and duplicated, not reordered.
 */
public class TestGetBulkPathArgumentOnlyReordered extends LuceneTestCase {

  public void testArgumentIsOnlyReordered() throws Exception {
    try (Directory dir = newDirectory()) {
      try (DirectoryTaxonomyWriter w = new DirectoryTaxonomyWriter(dir)) {
        w.addCategory(new FacetLabel("a")); // ordinal 1
        w.addCategory(new FacetLabel("b")); // ordinal 2
        w.addCategory(new FacetLabel("c")); // ordinal 3
      }
      try (DirectoryTaxonomyReader r = new DirectoryTaxonomyReader(dir)) {
        r.getPath(2); // puts ordinal 2 into the category cache

        int[] ordinals = {3, 2, 1};
        FacetLabel[] labels = r.getBulkPath(ordinals);
        // the labels themselves are right
        assertEquals(new FacetLabel("c"), labels[0]);
        assertEquals(new FacetLabel("b"), labels[1]);
        assertEquals(new FacetLabel("a"), labels[2]);

        // documented: the parameter may be reordered, i.e. it still holds the same elements
        int[] after = ordinals.clone();
        Arrays.sort(after);
        assertArrayEquals(
            "getBulkPath may only reorder its parameter, but it now holds "
                + Arrays.toString(ordinals),
            new int[] {1, 2, 3},
            after);
      }
    }
  }
}
