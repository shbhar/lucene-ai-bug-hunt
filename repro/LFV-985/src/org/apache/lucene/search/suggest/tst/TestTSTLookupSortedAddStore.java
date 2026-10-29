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
package org.apache.lucene.search.suggest.tst;

import java.util.concurrent.atomic.AtomicReference;
import org.apache.lucene.store.ByteBuffersDataOutput;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTSTLookupSortedAddStore extends LuceneTestCase {

  public void testStoreAfterSortedAdd() throws Exception {
    TSTLookup lookup = new TSTLookup();
    AtomicReference<Throwable> storeError = new AtomicReference<>();
    Thread t =
        new Thread(
            null,
            () -> {
              for (char c = '\u4E00'; c <= '\u9FFF'; c++) {
                lookup.add(String.valueOf(c), 1L);
              }
              try {
                lookup.store(new ByteBuffersDataOutput());
              } catch (Throwable e) {
                storeError.set(e);
              }
            },
            "tst-2MiB-stack",
            2L << 20);
    t.start();
    t.join();
    assertEquals(1, lookup.lookup("\u9FFF", false, 1).size());
    if (storeError.get() != null) {
      throw new AssertionError(
          "expected store() of the 20,992 add()ed keys to return true, but it threw "
              + storeError.get(),
          storeError.get());
    }
  }
}
