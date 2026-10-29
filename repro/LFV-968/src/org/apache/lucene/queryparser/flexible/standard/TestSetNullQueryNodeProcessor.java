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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSetNullQueryNodeProcessor extends LuceneTestCase {

  public void testSetNullProcessor() throws Exception {
    StandardQueryParser parser = new StandardQueryParser(new StandardAnalyzer());
    try {
      parser.setQueryNodeProcessor(null); // javadoc: "The argument can be null"
    } catch (NullPointerException e) {
      throw new AssertionError("expected null to switch off query processing, got " + e, e);
    }
    assertEquals(new TermQuery(new Term("f", "Foo")), parser.parse("Foo", "f"));
  }
}
