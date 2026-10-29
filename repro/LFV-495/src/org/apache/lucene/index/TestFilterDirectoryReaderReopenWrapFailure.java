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
package org.apache.lucene.index;

import java.io.IOException;
import java.io.UncheckedIOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFilterDirectoryReaderReopenWrapFailure extends LuceneTestCase {

  public void testFailedWrapOnReopenReleasesSharedSegment() throws IOException {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    w.addDocument(new Document());
    w.commit();
    DirectoryReader reader = new FailOnReopenReader(DirectoryReader.open(dir), false);
    LeafReader segment = reader.leaves().get(0).reader();
    w.addDocument(new Document());
    w.commit();
    w.close();

    expectThrows(UncheckedIOException.class, () -> DirectoryReader.openIfChanged(reader));
    // expected: refCount 1 (only 'reader' holds the segment); actual: 2, reopened reader leaked
    assertEquals(1, segment.getRefCount());
    reader.close();
    dir.close();
  }

  private static class FailOnReopenReader extends FilterDirectoryReader {
    FailOnReopenReader(DirectoryReader in, boolean fail) throws IOException {
      super(
          in,
          new SubReaderWrapper() {
            @Override
            public LeafReader wrap(LeafReader reader) {
              if (fail) {
                throw new UncheckedIOException(new IOException("simulated read error"));
              }
              return reader;
            }
          });
    }

    @Override
    protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
      return new FailOnReopenReader(in, true);
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return null;
    }
  }
}
