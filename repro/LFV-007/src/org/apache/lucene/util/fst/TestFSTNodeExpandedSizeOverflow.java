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
package org.apache.lucene.util.fst;

import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;

public class TestFSTNodeExpandedSizeOverflow extends LuceneTestCase {

  public void testRootNodeOf2048ArcsWithOneMiBArc() throws Exception {
    FSTCompiler<BytesRef> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE2, ByteSequenceOutputs.getSingleton()).build();
    BytesRef big = new BytesRef(new byte[(1 << 20) - 6]);
    for (int i = 0; i < 2048; i++) {
      BytesRef output = i == 0 ? big : new BytesRef(new byte[] {1});
      compiler.add(new IntsRef(new int[] {i * 32}, 0, 1), output);
    }
    FST<BytesRef> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    // 2048 arcs x 2^20 bytes = 2^31: expected key 0's output, actual a corrupt FST that throws
    assertEquals(big, Util.get(fst, new IntsRef(new int[] {0}, 0, 1)));
  }
}
