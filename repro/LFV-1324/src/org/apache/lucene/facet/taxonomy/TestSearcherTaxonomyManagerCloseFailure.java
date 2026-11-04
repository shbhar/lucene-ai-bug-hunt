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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSearcherTaxonomyManagerCloseFailure extends LuceneTestCase {

  public void testCloseReleasesTaxonomyReaderWhenIndexReaderCloseThrows() throws Exception {
    MockDirectoryWrapper indexDir = new MockDirectoryWrapper(random(), new ByteBuffersDirectory());
    Directory taxoDir = new ByteBuffersDirectory();
    try (IndexWriter w = new IndexWriter(indexDir, new IndexWriterConfig())) {
      w.addDocument(new Document());
    }
    new DirectoryTaxonomyWriter(taxoDir).close();
    DirectoryTaxonomyReader taxoReader = new DirectoryTaxonomyReader(taxoDir);
    SearcherTaxonomyManager mgr =
        new SearcherTaxonomyManager(DirectoryReader.open(indexDir), taxoReader, null);
    indexDir.failOn(
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper dir) throws IOException {
            throw new IOException("simulated I/O failure while closing the index reader");
          }
        });

    expectThrows(IOException.class, mgr::close);
    // expected: close() also releases the taxonomy reader (refCount 0); actual: refCount stays 1
    assertEquals(0, taxoReader.getRefCount());
  }
}
