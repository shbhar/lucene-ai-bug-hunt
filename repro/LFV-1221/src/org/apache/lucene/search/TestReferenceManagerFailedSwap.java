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
package org.apache.lucene.search;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestReferenceManagerFailedSwap extends LuceneTestCase {

  // Expected: acquire() succeeds after the failed maybeRefresh; actual: IllegalStateException.
  public void testAcquireAfterReleaseOfOldReferenceFails() throws Exception {
    ReferenceManager<AtomicInteger> manager =
        new ReferenceManager<>() {
          boolean failNextDecRef = true;

          {
            current = new AtomicInteger(1);
          }

          @Override
          protected void decRef(AtomicInteger ref) throws IOException {
            if (failNextDecRef) {
              failNextDecRef = false;
              throw new IOException("decRef failed");
            }
            ref.decrementAndGet();
          }

          @Override
          protected AtomicInteger refreshIfNeeded(AtomicInteger ref) {
            return new AtomicInteger(1);
          }

          @Override
          protected boolean tryIncRef(AtomicInteger ref) {
            return ref.get() > 0 && ref.incrementAndGet() > 0;
          }

          @Override
          protected int getRefCount(AtomicInteger ref) {
            return ref.get();
          }
        };
    expectThrows(IOException.class, manager::maybeRefresh);
    manager.release(manager.acquire());
  }
}
