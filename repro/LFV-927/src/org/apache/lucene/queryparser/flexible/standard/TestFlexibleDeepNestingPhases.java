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

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.precedence.PrecedenceQueryParser;
import org.apache.lucene.queryparser.flexible.standard.parser.StandardSyntaxParser;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryParserHelper.parse (and StandardQueryParser.parse, which overrides it) documents "@throws
 * QueryNodeException if something wrong happens along the three phases". Phase 1
 * (StandardSyntaxParser.parse) already turns any Error, including StackOverflowError, into a
 * QueryNodeParseException, so deeper and deeper nesting eventually fails as documented. Phases 2
 * and 3 (the QueryNodeProcessor pipeline and the QueryTreeBuilder) recurse over the node tree with
 * no such guard. For nested "+( ... )^2" groups each nesting level adds three nodes (modifier,
 * boost, group) but only one level of syntax recursion, so a StackOverflowError escapes from phase 2
 * at depths where phase 1 still succeeds.
 *
 * <p>The test grows the depth in 4% steps and stops at the first documented failure (a
 * QueryNodeException); it fails if a StackOverflowError escapes first, and then re-parses the same
 * text with phase 1 alone to show that the syntax phase accepts that depth.
 */
public class TestFlexibleDeepNestingPhases extends LuceneTestCase {

  interface Parser {
    void parse(String query) throws QueryNodeException;
  }

  private static void assertNoErrorBeforeDocumentedFailure(String name, Parser parser) {
    int lastParsedDepth = 0;
    for (int depth = 100; depth < 1_000_000; depth += Math.max(1, depth / 25)) {
      String query = "+(".repeat(depth) + "a" + ")^2".repeat(depth);
      try {
        parser.parse(query);
        lastParsedDepth = depth;
      } catch (QueryNodeException documented) {
        // too-deep nesting reported as documented; no Error escaped at any smaller depth
        assertTrue("vacuous: the first depth already failed: " + documented, lastParsedDepth > 0);
        return;
      } catch (StackOverflowError e) {
        String phase1;
        try {
          new StandardSyntaxParser().parse(query, "f");
          phase1 = "phase 1 (StandardSyntaxParser) alone accepts this depth";
        } catch (QueryNodeException | StackOverflowError p1) {
          phase1 = "phase 1 alone fails too: " + p1.getClass().getName();
        }
        throw new AssertionError(
            name
                + ".parse let a StackOverflowError escape at nesting depth "
                + depth
                + " ("
                + query.length()
                + " chars); "
                + phase1
                + "; documented outcome is a Query or a QueryNodeException",
            e);
      }
    }
    fail("no documented failure up to depth 1,000,000");
  }

  public void testStandardQueryParser() {
    StandardQueryParser qp = new StandardQueryParser(new MockAnalyzer(random()));
    assertNoErrorBeforeDocumentedFailure("StandardQueryParser", q -> qp.parse(q, "f"));
  }

  public void testPrecedenceQueryParser() {
    PrecedenceQueryParser qp = new PrecedenceQueryParser(new MockAnalyzer(random()));
    assertNoErrorBeforeDocumentedFailure("PrecedenceQueryParser", q -> qp.parse(q, "f"));
  }
}
