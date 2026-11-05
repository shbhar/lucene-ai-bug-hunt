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

public class TestReferenceManagerAfterCloseOnFailedRelease extends LuceneTestCase {

  public void testAfterCloseRunsWhenReleaseThrows() throws Exception {
    AtomicInteger afterCloseCalls = new AtomicInteger();
    ReferenceManager<Object> manager =
        new ReferenceManager<>() {
          {
            current = new Object();
          }

          @Override
          protected void decRef(Object ref) throws IOException {
            throw new IOException("closing the reference failed");
          }

          @Override
          protected Object refreshIfNeeded(Object ref) {
            return null;
          }

          @Override
          protected boolean tryIncRef(Object ref) {
            return true;
          }

          @Override
          protected int getRefCount(Object ref) {
            return 1;
          }

          @Override
          protected void afterClose() {
            afterCloseCalls.incrementAndGet();
          }
        };
    expectThrows(IOException.class, manager::close);
    manager.close();
    // expected: afterClose() ran once despite the failed release; actual: it never runs (0)
    assertEquals(1, afterCloseCalls.get());
  }
}
