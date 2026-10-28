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
package org.apache.lucene.queryparser.simple;

import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleQueryParser's class javadoc: "a person should be able to type whatever they want ... and
 * this parser will do its best to interpret what to search for no matter how poorly composed the
 * request may be" and "Any errors in query syntax will be ignored". parse(String) declares no
 * exception. Nested groups instead let a StackOverflowError escape: parseSubQuery ->
 * consumeSubQuery -> parseSubQuery recurses once per balanced '(' with no depth bound.
 */
public class TestSimpleDeepNesting extends LuceneTestCase {

  private static final int DEPTH = 100_000;

  public void testNestedParentheses() {
    String q = "(".repeat(DEPTH) + "a" + ")".repeat(DEPTH);
    SimpleQueryParser sqp = new SimpleQueryParser(new MockAnalyzer(random()), "f");
    try {
      assertNotNull(sqp.parse(q));
    } catch (StackOverflowError e) {
      throw new AssertionError(
          "SimpleQueryParser.parse let a StackOverflowError escape for "
              + q.length()
              + " chars of nested groups; documented: errors are ignored and it deciphers what it can",
          e);
    }
  }
}
