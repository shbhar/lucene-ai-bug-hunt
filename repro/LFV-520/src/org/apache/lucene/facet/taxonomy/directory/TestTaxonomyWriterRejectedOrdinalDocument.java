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

import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaxonomyWriterRejectedOrdinalDocument extends LuceneTestCase {

  public void testOrdinalAfterRejectedOrdinalDocument() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      DirectoryTaxonomyWriter tw =
          new ReindexingEnrichedDirectoryTaxonomyWriter(
              dir,
              (label, doc) -> {
                if (label.components[0].equals("bad")) {
                  doc.add(new StringField("big", "x".repeat(40_000), Field.Store.NO));
                }
              });
      expectThrows(IllegalArgumentException.class, () -> tw.addCategory(new FacetLabel("bad")));
      int b;
      try {
        b = tw.addCategory(new FacetLabel("b"));
      } catch (AlreadyClosedException e) {
        return; // a writer that closes itself after the rejection hands out no wrong ordinal
      }
      tw.close();
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(dir)) {
        assertEquals(
            "taxonomy ordinal of [b] vs the ordinal addCategory returned for it",
            reader.getOrdinal(new FacetLabel("b")),
            b);
      }
    }
  }
}
