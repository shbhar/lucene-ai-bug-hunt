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
package org.apache.lucene.queryparser.surround.parser;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The surround QueryParser.parse declares only ParseException, and parse2 already converts the
 * generated parser's TokenMgrError (an Error) into a ParseException. Nested groups instead let a
 * StackOverflowError escape: FieldsQuery -> OrQuery -> AndQuery -> NotQuery -> NQuery -> WQuery ->
 * PrimaryQuery -> FieldsQuery recurses seven frames per '(' with no depth bound.
 */
public class TestSurroundDeepNesting extends LuceneTestCase {

  private static final int DEPTH = 100_000;

  private static void assertParsesOrThrowsParseException(String query) {
    try {
      QueryParser.parse(query);
    } catch (ParseException expected) {
      // the documented failure mode
    } catch (StackOverflowError e) {
      throw new AssertionError(
          "surround QueryParser.parse let a StackOverflowError escape for "
              + query.length()
              + " chars of nested groups; declared outcome is a query or a ParseException",
          e);
    }
  }

  public void testNestedParentheses() {
    assertParsesOrThrowsParseException("(".repeat(DEPTH) + "a" + ")".repeat(DEPTH));
  }

  public void testNestedOrGroups() {
    assertParsesOrThrowsParseException("(a OR ".repeat(DEPTH) + "a" + ")".repeat(DEPTH));
  }
}
