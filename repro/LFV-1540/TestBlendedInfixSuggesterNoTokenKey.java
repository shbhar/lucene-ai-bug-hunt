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
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBlendedInfixSuggesterNoTokenKey extends LuceneTestCase {

  // expected: dune/30 (the highest weight); actual: ulysses/-2147483637 (the lowest weight)
  public void testStopWordKeyWithContexts() throws Exception {
    Set<BytesRef> books = Set.of(new BytesRef("books"));
    try (Analyzer analyzer = new EnglishAnalyzer();
        BlendedInfixSuggester suggester = new BlendedInfixSuggester(newDirectory(), analyzer)) {
      suggester.add(new BytesRef("dune"), books, 30, null);
      suggester.add(new BytesRef("emma"), books, 20, null);
      suggester.add(new BytesRef("ulysses"), books, 10, null);
      suggester.refresh();
      assertEquals("dune/30", suggester.lookup("the", books, false, 1).get(0).toString());
    }
  }

  // expected: to be/10 (the only suggestion of the context); actual: NullPointerException
  public void testStopWordKeyAndStopWordOnlySuggestion() throws Exception {
    Set<BytesRef> books = Set.of(new BytesRef("books"));
    try (Analyzer analyzer = new EnglishAnalyzer();
        BlendedInfixSuggester suggester = new BlendedInfixSuggester(newDirectory(), analyzer)) {
      suggester.add(new BytesRef("to be"), books, 10, null);
      suggester.refresh();
      assertEquals("to be/10", suggester.lookup("the", books, false, 1).get(0).toString());
    }
  }
}
