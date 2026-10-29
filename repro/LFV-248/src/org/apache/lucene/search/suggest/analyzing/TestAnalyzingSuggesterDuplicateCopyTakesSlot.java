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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAnalyzingSuggesterDuplicateCopyTakesSlot extends LuceneTestCase {

  public void testDuplicateCopyDoesNotUseSurfaceFormSlot() throws Exception {
    try (Directory tempDir = new ByteBuffersDirectory();
        Analyzer analyzer = new StandardAnalyzer()) {
      AnalyzingSuggester suggester =
          new AnalyzingSuggester(tempDir, "suggest", analyzer, analyzer, 0, 2, -1, true);
      suggester.build(new FileDictionary(new StringReader("a\t10\na\t5\nA\t3\n")));
      // expected: both surface forms of "a" fit in maxSurfaceFormsPerAnalyzedForm=2; actual: [a/10]
      assertEquals("[a/10, A/3]", suggester.lookup("a", false, 5).toString());
    }
  }
}
