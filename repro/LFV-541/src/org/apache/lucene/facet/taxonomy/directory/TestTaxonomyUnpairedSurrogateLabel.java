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

public class TestTaxonomyUnpairedSurrogateLabel extends LuceneTestCase {

  public void testSameLabelKeepsOrdinalAfterCacheFill() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      FacetLabel label = new FacetLabel("dim", "x\uD83D");
      int first;
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        first = tw.addCategory(label);
      }
      try (DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
        // with the label's own lookup, 11 misses (the default) fill the cache from the index
        for (int i = 0; i < 10; i++) {
          tw.addCategory(new FacetLabel("v" + i));
        }
        assertEquals("ordinal of the same label in a later session", first, tw.addCategory(label));
      }
    }
  }
}
