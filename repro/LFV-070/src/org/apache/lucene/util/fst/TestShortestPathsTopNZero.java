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

import java.util.Comparator;
import java.util.NoSuchElementException;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRefBuilder;

public class TestShortestPathsTopNZero extends LuceneTestCase {

  public void testTopNZeroReturnsEmptyResult() throws Exception {
    PositiveIntOutputs outputs = PositiveIntOutputs.getSingleton();
    FSTCompiler<Long> compiler = new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, outputs).build();
    compiler.add(Util.toIntsRef(new BytesRef("a"), new IntsRefBuilder()), 1L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());
    try {
      Util.TopResults<Long> top =
          Util.shortestPaths(
              fst, fst.getFirstArc(new FST.Arc<>()), 0L, Comparator.naturalOrder(), 0, false);
      assertEquals(0, top.topN.size());
    } catch (NoSuchElementException e) {
      throw new AssertionError("expected an empty result for topN=0, got " + e, e);
    }
  }
}
