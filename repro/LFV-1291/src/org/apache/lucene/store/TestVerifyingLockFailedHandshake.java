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
package org.apache.lucene.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestVerifyingLockFailedHandshake extends LuceneTestCase {

  public void testFailedObtainReleasesInnerLock() throws Exception {
    LockFactory inner = new SingleInstanceLockFactory();
    try (Directory dir = new ByteBuffersDirectory()) {
      LockFactory lf =
          new VerifyingLockFactory(
              inner, new ByteArrayInputStream(new byte[0]), new ByteArrayOutputStream());
      expectThrows(IllegalStateException.class, () -> lf.obtainLock(dir, "test.lock"));
      // expected: the failed obtainLock leaves the lock free; actual: "lock instance already obtained"
      inner.obtainLock(dir, "test.lock").close();
    }
  }
}
