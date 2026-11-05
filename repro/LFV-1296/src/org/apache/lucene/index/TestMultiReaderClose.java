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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiReaderClose extends LuceneTestCase {

  public void testUncheckedExceptionStillClosesOtherSubReaders() throws IOException {
    try (Directory dir = newDirectory()) {
      new IndexWriter(dir, new IndexWriterConfig()).close();
      DirectoryReader r1 = DirectoryReader.open(dir);
      DirectoryReader r2 = DirectoryReader.open(dir);
      r1.getReaderCacheHelper()
          .addClosedListener(
              _ -> {
                throw new IllegalStateException();
              });
      expectThrows(IllegalStateException.class, new MultiReader(r1, r2)::close);
      int refCount = r2.getRefCount();
      if (refCount > 0) {
        r2.close();
      }
      // expected: r2 closed too (refCount 0); actual: r2 left open (refCount 1)
      assertEquals(0, refCount);
    }
  }
}
