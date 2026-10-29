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

import java.io.IOException;
import org.apache.lucene.facet.taxonomy.FacetLabel;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReplaceTaxonomyFailure extends LuceneTestCase {

  public void testAddCategoryAfterReplaceTaxonomyReadFailure() throws IOException {
    FacetLabel label = new FacetLabel("dim", "a");
    try (Directory taxoDir = new ByteBuffersDirectory();
        Directory source = new ByteBuffersDirectory()) {
      new DirectoryTaxonomyWriter(source).close();
      Directory unreadableSource =
          new FilterDirectory(source) {
            @Override
            public IndexInput openInput(String name, IOContext context) throws IOException {
              if (name.startsWith("segments")) {
                return super.openInput(name, context);
              }
              throw new IOException("simulated read failure on " + name);
            }
          };
      DirectoryTaxonomyWriter writer = new DirectoryTaxonomyWriter(taxoDir);
      int ordinal = writer.addCategory(label);
      writer.commit();
      expectThrows(IOException.class, () -> writer.replaceTaxonomy(unreadableSource));
      try {
        ordinal = writer.addCategory(label);
      } catch (AlreadyClosedException writerClosedItselfOnFailure) {
      }
      writer.close();
      try (DirectoryTaxonomyReader reader = new DirectoryTaxonomyReader(taxoDir)) {
        // expected: dim/a at the ordinal the writer returned (2); actual: -1, not in the commit
        assertEquals("committed ordinal of dim/a", ordinal, reader.getOrdinal(label));
      }
    }
  }
}
