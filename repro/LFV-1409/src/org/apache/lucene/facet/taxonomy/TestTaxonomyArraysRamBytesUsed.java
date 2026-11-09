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
package org.apache.lucene.facet.taxonomy;

import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaxonomyArraysRamBytesUsed extends LuceneTestCase {

  // Expected: ramBytesUsed() includes the loaded parent array (>= 4 bytes per ordinal); actual: 41 bytes at any size.
  public void testRamBytesUsedCountsParentArray() throws Exception {
    try (Directory dir = newDirectory()) {
      try (DirectoryTaxonomyWriter writer = new DirectoryTaxonomyWriter(dir)) {
        for (int i = 0; i < 100; i++) {
          writer.addCategory(new FacetLabel("dim", "v" + i));
        }
      }
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        reader.getParallelTaxonomyArrays();
        long parentBytes = (long) Integer.BYTES * reader.getSize();
        long used = reader.ramBytesUsed();
        assertTrue("ramBytesUsed()=" + used + " < parent array " + parentBytes, used >= parentBytes);
      }
    }
  }
}
