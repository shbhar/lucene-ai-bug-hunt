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

import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestInfixSuggesterEnsureOpenFailure extends LuceneTestCase {

  public void testRefreshAfterFailedFirstAdd() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    try (AnalyzingInfixSuggester suggester =
        new AnalyzingInfixSuggester(dir, new StandardAnalyzer())) {
      Iterator<BytesRef> input = List.of(new BytesRef("lend me your ear")).iterator();
      suggester.build(
          new InputIterator.InputIteratorWrapper(() -> input.hasNext() ? input.next() : null));
      dir.failOn(
          new MockDirectoryWrapper.Failure() {
            boolean thrown;

            @Override
            public void eval(MockDirectoryWrapper d) throws IOException {
              if (thrown == false && callStackContains(SearcherManager.class, "<init>")) {
                thrown = true;
                throw new IOException("injected I/O error while opening the SearcherManager");
              }
            }
          });
      expectThrows(
          IOException.class, () -> suggester.add(new BytesRef("a penny saved"), null, 1, null));
      suggester.add(new BytesRef("a penny saved"), null, 1, null);
      // expected: refresh() makes the added suggestion visible; actual: AlreadyClosedException (this IndexWriter is closed)
      suggester.refresh();
      assertEquals(1, suggester.lookup("penn", 10, true, false).size());
    }
  }
}
