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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestBlendedInfixDuplicateEviction extends LuceneTestCase {

  public void testDuplicateEvictsDistinctSuggestion() throws Exception {
    String entries = "the lake\t1000\nlake\t950\nlake\t950\nmy lake\t920\n";
    try (Directory dir = newDirectory();
        Analyzer a = new StandardAnalyzer();
        BlendedInfixSuggester suggester = new BlendedInfixSuggester(dir, a)) {
      suggester.build(new FileDictionary(new StringReader(entries)));
      // num=3 gives [lake/950, the lake/900, my lake/828]; expected its top 2, actual: my lake 2nd
      assertEquals("[lake/950, the lake/900]", suggester.lookup("lake", false, 2).toString());
    }
  }
}
