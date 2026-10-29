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

import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReindexDropsUncommittedCategories extends LuceneTestCase {

  public void testUncommittedCategoryOrdinalIsNotPreserved() throws Exception {
    FacetLabel bob = new FacetLabel("Author", "Bob");
    try (Directory taxoDir = new ByteBuffersDirectory();
        ReindexingEnrichedDirectoryTaxonomyWriter taxoWriter =
            new ReindexingEnrichedDirectoryTaxonomyWriter(taxoDir, null)) {
      taxoWriter.commit();
      int bobOrd = taxoWriter.addCategory(bob);
      taxoWriter.reindexWithNewOrdinalData((label, doc) -> {});
      try (DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir)) {
        // uncommitted Author/Bob must keep its ordinal: expected 2, main gives -1 (dropped)
        assertEquals(bobOrd, taxoReader.getOrdinal(bob));
      }
    }
  }
}
