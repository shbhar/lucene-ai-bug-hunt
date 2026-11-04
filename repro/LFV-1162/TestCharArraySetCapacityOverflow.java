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
package org.apache.lucene.analysis;

import com.carrotsearch.randomizedtesting.annotations.ThreadLeakScope;
import org.apache.lucene.tests.util.LuceneTestCase;

@ThreadLeakScope(ThreadLeakScope.Scope.NONE)
public class TestCharArraySetCapacityOverflow extends LuceneTestCase {

  public void testHugeCapacityReturnsOrThrows() throws Exception {
    // expected: returns or throws (858_993_459 throws OutOfMemoryError); actual: spins forever
    Thread t =
        new Thread(
            () -> {
              try {
                new CharArraySet(858_993_460, false);
              } catch (Throwable _) {
              }
            });
    t.setDaemon(true);
    t.start();
    t.join(10_000);
    assertFalse("new CharArraySet(858_993_460, false) still running after 10 s", t.isAlive());
  }
}
