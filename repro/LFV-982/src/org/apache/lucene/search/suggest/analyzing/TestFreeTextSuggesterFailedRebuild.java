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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFreeTextSuggesterFailedRebuild extends LuceneTestCase {

  public void testRejectedRebuildKeepsUnigramScores() throws Exception {
    FreeTextSuggester suggester = new FreeTextSuggester(new StandardAnalyzer());
    String text = "foo bar baz\nfoo bar\nfoo bee\nfun\n";
    suggester.build(
        new FileDictionary(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)))
            .getEntryIterator());
    String before = suggester.lookup("f", 2).toString();
    expectThrows(IllegalArgumentException.class, () -> suggester.build(InputIterator.EMPTY));
    // expected: foo 3/8 and fun 1/8 of Long.MAX_VALUE as before; actual: both Long.MAX_VALUE
    assertEquals(before, suggester.lookup("f", 2).toString());
  }
}
