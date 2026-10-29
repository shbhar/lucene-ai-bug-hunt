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
package org.apache.lucene.util.bkd;

import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBKDWriterSortBufferOver2GB extends LuceneTestCase {

  public void testAddWithSortBufferAbove2048MB() throws Exception {
    // 16 dims (8 indexed) x 16 bytes = 260 bytes per point; 8.4M points = 2,184,000,000 bytes
    BKDConfig config = new BKDConfig(16, 8, 16, 512);
    try (Directory dir = new ByteBuffersDirectory();
        BKDWriter w = new BKDWriter(1, dir, "tmp", config, 2200, 8_400_000L)) {
      try {
        w.add(new byte[config.packedBytesLength()], 0);
      } catch (NegativeArraySizeException e) {
        throw new AssertionError("expected add() to keep the point on heap or spill it, got " + e, e);
      }
    }
  }
}
