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
package org.apache.lucene.search.suggest.analyzing;

import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestAnalyzingSuggesterPayloadSurfaceSlice extends LuceneTestCase {

  public void testSurfaceFormSliceWithPayload() throws Exception {
    BytesRef surface = new BytesRef(new byte[] {0x1F, 'a', 'b'}, 1, 2);
    InputIterator it =
        new InputIterator() {
          boolean done;

          @Override
          public BytesRef next() {
            if (done) {
              return null;
            }
            done = true;
            return surface;
          }

          @Override
          public long weight() {
            return 1;
          }

          @Override
          public BytesRef payload() {
            return new BytesRef("p");
          }

          @Override
          public boolean hasPayloads() {
            return true;
          }

          @Override
          public Set<BytesRef> contexts() {
            return null;
          }

          @Override
          public boolean hasContexts() {
            return false;
          }
        };
    try (Directory dir = newDirectory();
        Analyzer analyzer = new MockAnalyzer(random())) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(dir, "suggest", analyzer);
      try {
        suggester.build(it);
      } catch (IllegalArgumentException e) {
        throw new AssertionError(
            "expected surface form \"ab\" to be accepted, but build rejected it", e);
      }
      assertEquals("ab", suggester.lookup("a", false, 1).get(0).key.toString());
    }
  }
}
