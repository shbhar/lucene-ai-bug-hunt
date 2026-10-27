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
import org.apache.lucene.document.Document;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * IndexReader#decRef javadoc: "If the refCount drops to 0, then this reader is closed. If an
 * exception is hit, the refCount is unchanged." Since LUCENE-5553 decRef sets the reader closed and
 * keeps the count at 0 when doClose() or a closed listener throws.
 */
public class TestDecRefExceptionRefCount extends LuceneTestCase {

  public void testRefCountAfterFailedFinalDecRef() throws Exception {
    Directory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    w.addDocument(new Document());
    w.close();
    DirectoryReader reader = DirectoryReader.open(dir);
    reader
        .getReaderCacheHelper()
        .addClosedListener(
            key -> {
              throw new IOException("listener failure");
            });
    assertEquals(1, reader.getRefCount());

    expectThrows(IOException.class, reader::decRef);

    assertEquals("javadoc: if an exception is hit, the refCount is unchanged", 1, reader.getRefCount());
    dir.close();
  }
}
