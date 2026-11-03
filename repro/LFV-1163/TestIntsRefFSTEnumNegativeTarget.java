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
import org.apache.lucene.util.IntsRef;

public class TestIntsRefFSTEnumNegativeTarget extends LuceneTestCase {

  public void testSeekCeilNegativeIntAfterTermPrefix() throws Exception {
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE4, PositiveIntOutputs.getSingleton()).build();
    compiler.add(new IntsRef(new int[] {5}, 0, 1), 1L);
    compiler.add(new IntsRef(new int[] {5, 3}, 0, 2), 2L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    IntsRef target = new IntsRef(new int[] {5, Integer.MIN_VALUE}, 0, 2);
    // expected [5, 3] (smallest term >= [5, MIN_VALUE]); actual [5], a term below the target
    assertEquals(
        new IntsRef(new int[] {5, 3}, 0, 2), new IntsRefFSTEnum<>(fst).seekCeil(target).input);
  }
}
