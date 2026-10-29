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
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.store.MockDirectoryWrapper;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestInfixFailedBuildAfterCommit extends LuceneTestCase {

  private static List<String> lookup(AnalyzingInfixSuggester suggester) throws IOException {
    return suggester.lookup("a", 10, true, false).stream()
        .map(r -> r.key.toString())
        .sorted()
        .toList();
  }

  public void testAddAfterFailedBuild() throws Exception {
    MockDirectoryWrapper dir = newMockDirectory();
    MockDirectoryWrapper.Failure failure =
        new MockDirectoryWrapper.Failure() {
          @Override
          public void eval(MockDirectoryWrapper d) throws IOException {
            if (doFail && callStackContains(SearcherManager.class, "<init>")) {
              doFail = false;
              throw new IOException("simulated I/O error while build() opens the new searcher");
            }
          }
        };
    dir.failOn(failure);
    try (Analyzer analyzer = new MockAnalyzer(random());
        AnalyzingInfixSuggester suggester = new AnalyzingInfixSuggester(dir, analyzer)) {
      suggester.build(new FileDictionary(new StringReader("old apple")));
      failure.setDoFail();
      expectThrows(
          IOException.class,
          () -> suggester.build(new FileDictionary(new StringReader("new avocado"))));
      suggester.refresh();
      List<String> expected = new ArrayList<>(lookup(suggester));
      expected.add("another apricot");
      expected.sort(null);
      suggester.add(new BytesRef("another apricot"), null, 1, null);
      suggester.refresh();
      // expected the served [old apple] plus the added one; actual: [another apricot, new avocado]
      assertEquals(expected, lookup(suggester));
    }
  }
}
