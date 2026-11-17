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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.facet.taxonomy.writercache.LruTaxonomyWriterCache;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.SerialMergeScheduler;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.FilterIndexInput;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTaxonomyWriterFailedCacheFillClose extends LuceneTestCase {

  public void testAddCategoryAfterReaderCloseFailsInCacheFill() throws IOException {
    AtomicBoolean failNextClose = new AtomicBoolean();
    Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            return new FilterIndexInput(name, in.openInput(name, context)) {
              @Override
              public IndexInput clone() {
                return in.clone();
              }

              @Override
              public void close() throws IOException {
                in.close();
                if (failNextClose.getAndSet(false)) {
                  throw new IOException("simulated close failure");
                }
              }
            };
          }
        };
    new DirectoryTaxonomyWriter(dir).close();
    DirectoryTaxonomyWriter writer =
        new DirectoryTaxonomyWriter(dir, OpenMode.APPEND, new LruTaxonomyWriterCache(12)) {
          @Override
          protected IndexWriterConfig createIndexWriterConfig(OpenMode openMode) {
            return super.createIndexWriterConfig(openMode)
                .setMergeScheduler(new SerialMergeScheduler());
          }
        };
    for (int i = 1; i <= 10; i++) {
      writer.addCategory(new FacetLabel("c" + i));
      writer.commit();
    }
    failNextClose.set(true);
    expectThrows(IOException.class, () -> writer.addCategory(new FacetLabel("c11")));
    assertEquals(11, writer.addCategory(new FacetLabel("c11")));
    // Expected: the still-open writer adds c12 and returns 12; actual: AlreadyClosedException.
    assertEquals(12, writer.addCategory(new FacetLabel("c12")));
    writer.close();
    dir.close();
  }
}
