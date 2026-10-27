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
package org.apache.lucene.queryparser.flexible.standard.nodes;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.apache.lucene.queryparser.flexible.core.nodes.QueryNode;
import org.apache.lucene.queryparser.flexible.core.parser.EscapeQuerySyntax;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.parser.EscapeQuerySyntaxImpl;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * GITHUB#7865 (CHANGES, 10.3): "AbstractRangeQueryNode#toQueryString(EscapeQuerySyntax) now returns a string which
 * can be parsed back into the original node". These range nodes are the ones StandardQueryParser itself builds, and
 * their query strings either do not parse, throw, or parse into a different range.
 */
public class TestRangeNodeToQueryStringRoundTrip extends LuceneTestCase {
  private static final EscapeQuerySyntax ESCAPER = new EscapeQuerySyntaxImpl();

  private static StandardQueryParser parser() {
    StandardQueryParser parser = new StandardQueryParser(new KeywordAnalyzer());
    parser.setPointsConfigMap(
        Map.of("i", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class)));
    return parser;
  }

  /** The range node of {@code query} after the parser's processor pipeline (the node the builder turns into a Query). */
  private static AbstractRangeQueryNode<?> processedRangeNode(StandardQueryParser p, String query) throws Exception {
    QueryNode tree = p.getQueryNodeProcessor().process(p.getSyntaxParser().parse(query, "df"));
    List<AbstractRangeQueryNode<?>> ranges = new ArrayList<>();
    collect(tree, ranges);
    assertEquals(1, ranges.size());
    return ranges.get(0);
  }

  private static void collect(QueryNode n, List<AbstractRangeQueryNode<?>> out) {
    if (n instanceof AbstractRangeQueryNode<?> r) {
      out.add(r);
    } else if (n.getChildren() != null) {
      for (QueryNode c : n.getChildren()) collect(c, out);
    }
  }

  private static void assertRoundTrip(StandardQueryParser p, String query, AbstractRangeQueryNode<?> node)
      throws Exception {
    Query expected = p.parse(query, "df");
    String queryString = node.toQueryString(ESCAPER).toString();
    Query actual = p.parse(queryString, "df");
    assertEquals("query string of " + query + " was: " + queryString, expected, actual);
  }

  /** Open upper bound of a term range: the node's query string is "f:[a TO ]", a syntax error. */
  public void testOpenTermRange() throws Exception {
    StandardQueryParser p = parser();
    String query = "f:[a TO *]";
    assertRoundTrip(p, query, processedRangeNode(p, query));
  }

  /** Open bound of a point range: toQueryString throws IllegalArgumentException (NumberFormat.format(null)). */
  public void testOpenPointRange() throws Exception {
    StandardQueryParser p = parser();
    String query = "i:[1 TO *]";
    assertRoundTrip(p, query, processedRangeNode(p, query));
  }

  /** A literal '*' bound (escaped in the query) comes back as an open bound. */
  public void testLiteralStarBound() throws Exception {
    StandardQueryParser p = parser();
    String query = "f:[\\* TO a]";
    assertRoundTrip(p, query, processedRangeNode(p, query));
  }

  /** A quoted bound with a space: the query string "f:[a\ b TO c]" is a syntax error (the range lexer stops at the space). */
  public void testBoundWithSpace() throws Exception {
    StandardQueryParser p = parser();
    String query = "f:[\"a b\" TO c]";
    // the node straight from the syntax parser, before any processing
    QueryNode tree = p.getSyntaxParser().parse(query, "df");
    assertTrue(tree instanceof TermRangeQueryNode);
    assertRoundTrip(p, query, (TermRangeQueryNode) tree);
  }

  /**
   * In-tree effect: BooleanQueryNodeBuilder formats its TOO_MANY_BOOLEAN_CLAUSES message with toQueryString, so a query
   * over the clause limit that contains an open point range makes parse() throw IllegalArgumentException instead of the
   * documented QueryNodeException.
   */
  public void testTooManyClausesWithOpenPointRange() throws Exception {
    StandardQueryParser p = parser();
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i <= IndexSearcher.getMaxClauseCount(); i++) sb.append("t").append(i).append(' ');
    // control: without the open point range the documented exception comes out
    expectThrows(QueryNodeException.class, () -> p.parse(sb.toString(), "df"));
    sb.append("i:[1 TO *]");
    expectThrows(QueryNodeException.class, () -> p.parse(sb.toString(), "df")); // fails: IllegalArgumentException
  }
}
