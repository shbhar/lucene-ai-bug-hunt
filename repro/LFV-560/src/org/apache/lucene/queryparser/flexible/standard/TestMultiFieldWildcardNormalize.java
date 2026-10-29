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
package org.apache.lucene.queryparser.flexible.standard;

import java.util.Collections;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMultiFieldWildcardNormalize extends LuceneTestCase {

  public void testPrefixUsesExpandedFieldAnalyzer() throws Exception {
    Analyzer a =
        new PerFieldAnalyzerWrapper(
            new WhitespaceAnalyzer(), Collections.singletonMap("title", new StandardAnalyzer()));
    StandardQueryParser qp = new StandardQueryParser(a);
    qp.setMultiFields(new CharSequence[] {"title"});
    // expected title:luc* (as for "title:Luc*"); actual title:Luc* (normalized with field null)
    assertEquals(qp.parse("title:Luc*", null), qp.parse("Luc*", null));
  }
}
