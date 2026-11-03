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

public class TestFSTEnumSeekAfterMaxLabel extends LuceneTestCase {

  public void testSeekCeilToPrefixAfterMaxLabel() throws Exception {
    IntsRef shortTerm = new IntsRef(new int[] {3}, 0, 1);
    IntsRef longTerm = new IntsRef(new int[] {3, Integer.MAX_VALUE}, 0, 2);
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE4, PositiveIntOutputs.getSingleton()).build();
    compiler.add(shortTerm, 1L);
    compiler.add(longTerm, 2L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());

    IntsRefFSTEnum<Long> fstEnum = new IntsRefFSTEnum<>(fst);
    fstEnum.seekCeil(longTerm);
    // expected: [3] (it exists, so it is its own ceiling); actual: [3, 7fffffff]
    assertEquals(shortTerm, fstEnum.seekCeil(shortTerm).input);
  }
}
