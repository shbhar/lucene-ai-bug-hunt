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
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FilterDirectory;
import org.apache.lucene.store.IOContext;
import org.apache.lucene.store.IndexInput;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectoryTaxonomyWriterCtorFailure extends LuceneTestCase {

  private volatile boolean failNextCommitRead;

  public void testWriterOpensAfterConstructorFailedReadingCommit() throws Exception {
    try (Directory dir =
        new FilterDirectory(new ByteBuffersDirectory()) {
          @Override
          public IndexInput openInput(String name, IOContext context) throws IOException {
            if (failNextCommitRead
                && name.startsWith("segments_")
                && StackWalker.getInstance()
                    .walk(s -> s.noneMatch(f -> f.getClassName().equals(IndexWriter.class.getName())))) {
              failNextCommitRead = false;
              throw new IOException("simulated I/O error reading " + name);
            }
            return super.openInput(name, context);
          }
        }) {
      new DirectoryTaxonomyWriter(dir).close();
      failNextCommitRead = true;
      expectThrows(IOException.class, () -> new DirectoryTaxonomyWriter(dir));
      // expected: a new writer opens after the failed constructor; actual: LockObtainFailedException
      try {
        new DirectoryTaxonomyWriter(dir).close();
      } catch (LockObtainFailedException e) {
        fail("the failed constructor left the taxonomy write-locked: " + e);
      }
    }
  }
}
