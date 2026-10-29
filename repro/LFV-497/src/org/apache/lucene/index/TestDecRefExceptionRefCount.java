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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDecRefExceptionRefCount extends LuceneTestCase {

  public void testRefCountUnchangedAfterFailedDecRef() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
      DirectoryReader reader = DirectoryReader.open(dir);
      reader
          .getReaderCacheHelper()
          .addClosedListener(
              key -> {
                throw new IOException("closed listener failure");
              });
      expectThrows(IOException.class, reader::decRef);
      // decRef javadoc: "If an exception is hit, the refCount is unchanged" (1); main leaves 0
      assertEquals("refCount after the failed decRef", 1, reader.getRefCount());
    }
  }
}
