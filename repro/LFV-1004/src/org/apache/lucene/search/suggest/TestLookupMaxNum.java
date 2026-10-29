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
package org.apache.lucene.search.suggest;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.spell.PlainTextDictionary;
import org.apache.lucene.search.suggest.analyzing.AnalyzingInfixSuggester;
import org.apache.lucene.search.suggest.analyzing.FreeTextSuggester;
import org.apache.lucene.search.suggest.fst.WFSTCompletionLookup;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestLookupMaxNum extends LuceneTestCase {

  public void testLookupWithMaxNum() throws IOException {
    // expected the 1 suggestion, actual OutOfMemoryError: each lookup allocates num result slots
    try (Directory dir1 = new ByteBuffersDirectory();
        Directory dir2 = new ByteBuffersDirectory();
        AnalyzingInfixSuggester infix = new AnalyzingInfixSuggester(dir1, new StandardAnalyzer())) {
      assertEquals(1, lookupAll(infix));
      assertEquals(1, lookupAll(new WFSTCompletionLookup(dir2, "wfst")));
      assertEquals(1, lookupAll(new FreeTextSuggester(new StandardAnalyzer())));
    }
  }

  private static int lookupAll(Lookup lookup) throws IOException {
    lookup.build(new PlainTextDictionary(new StringReader("lucene")));
    return lookup.lookup("luc", false, Integer.MAX_VALUE).size();
  }
}
