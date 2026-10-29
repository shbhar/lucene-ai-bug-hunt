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
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBlendedInfixSuggesterNumOverflow extends LuceneTestCase {

  public void testLookupWithIntegerMaxValueNum() throws IOException {
    try (Analyzer analyzer = new StandardAnalyzer();
        BlendedInfixSuggester suggester =
            new BlendedInfixSuggester(new ByteBuffersDirectory(), analyzer)) {
      suggester.build(new PlainTextDictionary(new StringReader("lucene")));
      List<LookupResult> results;
      try {
        results = suggester.lookup("luc", false, Integer.MAX_VALUE);
      } catch (IllegalArgumentException e) {
        // expected the 1 suggestion; actual: num * numFactor (10) wraps to -10 and is rejected
        throw new AssertionError("expected 1 suggestion for num=Integer.MAX_VALUE, got " + e, e);
      }
      assertEquals(1, results.size());
    }
  }
}
