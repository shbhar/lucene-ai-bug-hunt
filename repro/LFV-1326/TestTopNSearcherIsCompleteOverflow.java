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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;

public class TestTopNSearcherIsCompleteOverflow extends LuceneTestCase {

  public void testIsCompleteWithMaxTopN() throws Exception {
    FSTCompiler<Long> compiler =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton()).build();
    compiler.add(Util.toIntsRef(new BytesRef("a"), new IntsRefBuilder()), 1L);
    compiler.add(Util.toIntsRef(new BytesRef("b"), new IntsRefBuilder()), 2L);
    FST<Long> fst = FST.fromFSTReader(compiler.compile(), compiler.getFSTReader());

    Util.TopNSearcher<Long> searcher =
        new Util.TopNSearcher<>(fst, Integer.MAX_VALUE, 1, Comparator.naturalOrder()) {
          @Override
          protected boolean acceptResult(IntsRef input, Long output) {
            return output != 1L;
          }
        };
    searcher.addStartPaths(fst.getFirstArc(new FST.Arc<>()), 0L, false, new IntsRefBuilder());
    Util.TopResults<Long> results = searcher.search();

    // the depth-1 queue dropped "b", an accepted result, so the list is empty and not complete
    assertEquals(0, results.topN.size());
    assertFalse(results.isComplete);
  }
}
