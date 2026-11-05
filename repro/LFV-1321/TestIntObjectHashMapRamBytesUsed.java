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
package org.apache.lucene.internal.hppc;

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIntObjectHashMapRamBytesUsed extends LuceneTestCase {

  public void testNullValueAddsNoBytes() {
    // expected: a null value adds 0 bytes; actual: 256 (the cursor is sized, not the value)
    IntObjectHashMap<Object> map = new IntObjectHashMap<>();
    long empty = map.ramBytesUsed();
    map.put(1, null);
    assertEquals(empty, map.ramBytesUsed());
  }
}
