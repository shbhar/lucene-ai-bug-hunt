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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaxonomyWriterFailedRefreshDuplicatesOrdinal extends LuceneTestCase {

  private volatile boolean failNextSegmentReaderOpen;

  public void testExistingCategoryKeepsOrdinalAfterFailedEvictionRefresh() throws Exception {
    try (Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            if (failNextSegmentReaderOpen
                && StackWalker.getInstance()
                    .walk(
                        s ->
                            s.anyMatch(
                                f ->
                                    f.getClassName()
                                        .equals("org.apache.lucene.index.SegmentCoreReaders")))) {
              failNextSegmentReaderOpen = false;
              throw new IOException("simulated I/O error opening the near-real-time reader");
            }
            return super.openInput(name, context);
          }
        };
        DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(dir)) {
      for (int i = 1; i < 4000; i++) {
        tw.addCategory(new FacetLabel("l" + i));
      }
      failNextSegmentReaderOpen = true;
      expectThrows(IOException.class, () -> tw.addCategory(new FacetLabel("x")));
      // expected: the existing category l1 keeps ordinal 1; actual: it is indexed again as 4001
      assertEquals("ordinal returned for existing category l1", 1, tw.addCategory(new FacetLabel("l1")));
    }
  }
}
