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
import org.apache.lucene.document.Document;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyReader;
import org.apache.lucene.facet.taxonomy.directory.DirectoryTaxonomyWriter;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.FilterDirectoryReader;
import org.apache.lucene.index.FilterLeafReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * When reopening the taxonomy fails in SearcherTaxonomyManager.refreshIfNeeded, the manager closes
 * the index reader it has just reopened. If that close also fails, the catch block calls {@code
 * t2.addSuppressed(t2)}, which itself throws IllegalArgumentException("Self-suppression not
 * permitted"); the taxonomy failure is lost and an unchecked exception escapes maybeRefresh().
 *
 * <p>Both failures are injected through public extension points: a FilterDirectoryReader whose
 * reopened instances fail on close, and a DirectoryTaxonomyReader whose doOpenIfChanged fails.
 */
public class TestSearcherTaxonomyManagerSelfSuppression extends LuceneTestCase {

  /** Identity wrapper; reopened instances throw from close(). */
  private static final class FailOnCloseReader extends FilterDirectoryReader {
    private final boolean failOnClose;

    FailOnCloseReader(DirectoryReader in, boolean failOnClose) throws IOException {
      super(
          in,
          new SubReaderWrapper() {
            @Override
            public LeafReader wrap(LeafReader reader) {
              return new FilterLeafReader(reader) {
                @Override
                public CacheHelper getCoreCacheHelper() {
                  return in.getCoreCacheHelper();
                }

                @Override
                public CacheHelper getReaderCacheHelper() {
                  return in.getReaderCacheHelper();
                }
              };
            }
          });
      this.failOnClose = failOnClose;
    }

    @Override
    protected DirectoryReader doWrapDirectoryReader(DirectoryReader in) throws IOException {
      return new FailOnCloseReader(in, true);
    }

    @Override
    protected void doClose() throws IOException {
      super.doClose();
      if (failOnClose) {
        throw new IOException("index reader close failed");
      }
    }

    @Override
    public CacheHelper getReaderCacheHelper() {
      return in.getReaderCacheHelper();
    }
  }

  /** A taxonomy reader whose reopen fails, e.g. on an I/O error. */
  private static final class FailingReopenTaxonomyReader extends DirectoryTaxonomyReader {
    FailingReopenTaxonomyReader(DirectoryTaxonomyWriter writer) throws IOException {
      super(writer);
    }

    @Override
    protected DirectoryTaxonomyReader doOpenIfChanged() throws IOException {
      throw new IOException("taxonomy reopen failed");
    }
  }

  public void testTaxonomyReopenFailureIsReported() throws Exception {
    Directory indexDir = newDirectory();
    Directory taxoDir = newDirectory();
    IndexWriter w = new IndexWriter(indexDir, newIndexWriterConfig());
    DirectoryTaxonomyWriter tw = new DirectoryTaxonomyWriter(taxoDir);
    // "Creates this from already opened IndexReader and DirectoryTaxonomyReader instances"
    SearcherTaxonomyManager mgr =
        new SearcherTaxonomyManager(
            new FailOnCloseReader(DirectoryReader.open(w), false),
            new FailingReopenTaxonomyReader(tw),
            null);
    try {
      w.addDocument(new Document()); // so that the index reader is reopened
      // maybeRefresh declares IOException; the taxonomy failure should come out (the close
      // failure at most as a suppressed exception)
      Throwable thrown = expectThrows(Throwable.class, mgr::maybeRefresh);
      assertTrue(
          "expected the taxonomy reopen IOException, got " + thrown,
          thrown instanceof IOException && "taxonomy reopen failed".equals(thrown.getMessage()));
    } finally {
      mgr.close();
      tw.close();
      w.close();
      taxoDir.close();
      indexDir.close();
    }
  }
}
