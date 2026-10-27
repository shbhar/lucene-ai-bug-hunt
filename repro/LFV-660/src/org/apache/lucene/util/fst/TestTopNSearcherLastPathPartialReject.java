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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefBuilder;
import org.apache.lucene.util.IntsRef;
import org.apache.lucene.util.IntsRefBuilder;

/**
 * With maxQueueDepth == topN, Util.TopNSearcher drops its queue before following the last path
 * ("Last path -- don't bother w/ queue anymore"). If acceptPartialPath rejects that path further
 * down, the search ends: an accepted completion is never visited, the rejection is not counted, and
 * TopResults.isComplete is true. Public API only (public class, protected hook).
 */
public class TestTopNSearcherLastPathPartialReject extends LuceneTestCase {

  private static List<String> search(int topN, int maxQueueDepth) throws Exception {
    FSTCompiler<Long> c =
        new FSTCompiler.Builder<>(FST.INPUT_TYPE.BYTE1, PositiveIntOutputs.getSingleton()).build();
    IntsRefBuilder scratch = new IntsRefBuilder();
    c.add(Util.toIntsRef(new BytesRef("aa"), scratch), 0L);
    c.add(Util.toIntsRef(new BytesRef("ab"), scratch), 0L);
    FST<Long> fst = FST.fromFSTReader(c.compile(), c.getFSTReader());
    Util.TopNSearcher<Long> s =
        new Util.TopNSearcher<>(fst, topN, maxQueueDepth, Comparator.naturalOrder()) {
          @Override
          protected boolean acceptPartialPath(Util.FSTPath<Long> path) {
            // reject every path whose input starts with "aa"
            IntsRef in = path.input.get();
            return !(in.length >= 2 && in.ints[in.offset] == 'a' && in.ints[in.offset + 1] == 'a');
          }
        };
    s.addStartPaths(
        fst.getFirstArc(new FST.Arc<>()), fst.outputs.getNoOutput(), true, new IntsRefBuilder());
    Util.TopResults<Long> res = s.search();
    List<String> out = new ArrayList<>();
    for (Util.Result<Long> r : res) {
      out.add(Util.toBytesRef(r.input(), new BytesRefBuilder()).utf8ToString() + "/" + r.output());
    }
    out.add("isComplete=" + res.isComplete);
    return out;
  }

  /** Control: one spare queue slot, "ab" is found. */
  public void testControlDeeperQueue() throws Exception {
    assertEquals(List.of("ab/0", "isComplete=true"), search(1, 2));
  }

  /** maxQueueDepth == topN (what Util.shortestPaths uses): nothing is returned, isComplete=true. */
  public void testQueueDepthEqualsTopN() throws Exception {
    assertEquals(List.of("ab/0", "isComplete=true"), search(1, 1));
  }
}
