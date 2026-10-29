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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReindexOrdinalDataFailureAtomicity extends LuceneTestCase {

  public void testFailedReindexThenCloseKeepsOrdinals() throws Exception {
    FacetLabel label = new FacetLabel("d", "a");
    try (Directory dir = newDirectory()) {
      ReindexingEnrichedDirectoryTaxonomyWriter writer =
          new ReindexingEnrichedDirectoryTaxonomyWriter(dir, null);
      int ordinal = writer.addCategory(label);
      writer.commit();
      expectThrows(
          IllegalStateException.class,
          () -> {
            try (writer) {
              writer.reindexWithNewOrdinalData(
                  (path, doc) -> {
                    if (path.equals(label)) {
                      throw new IllegalStateException("appender failure");
                    }
                  });
            }
          });
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        // expected: d/a keeps its ordinal after the failed reindex and close(); actual: -1 (close() committed the half-rebuilt taxonomy)
        assertEquals(ordinal, reader.getOrdinal(label));
      }
    }
  }
}
