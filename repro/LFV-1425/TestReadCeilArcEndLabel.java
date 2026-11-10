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

public class TestReadCeilArcEndLabel extends LuceneTestCase {

  public void testCeilOfEndLabelOnNonFinalNode() throws Exception {
    Outputs<Object> outputs = NoOutputs.getSingleton();
    FSTCompiler<Object> c = new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, outputs).build();
    c.add(Util.toIntsRef(new BytesRef("a"), new IntsRefBuilder()), outputs.getNoOutput());
    FST<Object> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    // expected: arc 'a' (the first arc >= END_LABEL = -1); actual: null
    FST.Arc<Object> ceil =
        Util.readCeilArc(
            FST.END_LABEL,
            fst,
            fst.getFirstArc(new FST.Arc<>()),
            new FST.Arc<>(),
            fst.getBytesReader());
    assertNotNull(ceil);
    assertEquals('a', ceil.label());
  }
}
