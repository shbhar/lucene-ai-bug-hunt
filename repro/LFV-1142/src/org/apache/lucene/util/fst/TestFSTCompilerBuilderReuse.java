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
import org.apache.lucene.util.IntsRefBuilder;

public class TestFSTCompilerBuilderReuse extends LuceneTestCase {

  public void testSecondBuildFromSameBuilder() throws Exception {
    FSTCompiler.Builder<Long> builder =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton());
    IntsRefBuilder scratch = new IntsRefBuilder();

    FSTCompiler<Long> c1 = builder.build();
    c1.add(Util.toIntsRef(new BytesRef("a"), scratch), 1L);
    FST.fromFSTReader(c1.compile(), c1.getFSTReader());

    FSTCompiler<Long> c2 = builder.build();
    c2.add(Util.toIntsRef(new BytesRef("b"), scratch), 2L);
    FST<Long> fst2 = FST.fromFSTReader(c2.compile(), c2.getFSTReader());

    // expected: the second FST maps b -> 2; actual: null (-da), AssertionError in compile() (-ea)
    assertEquals(Long.valueOf(2), Util.get(fst2, new BytesRef("b")));
  }
}
