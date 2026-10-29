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
package org.apache.lucene.queryparser.xml;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * CoreParser.parse(InputStream) and CoreParser.getQuery(Element) declare only ParserException
 * ("Thrown when the xml queryparser encounters invalid syntax/configuration"). Deeply nested
 * BooleanQuery elements instead make the builders recurse once per level
 * (BooleanQueryBuilder.getQuery -> QueryBuilderFactory.getQuery) until the thread stack overflows.
 *
 * <p>Public API only. The input is well-formed XML that the parser itself accepts at small depths.
 */
public class TestXmlQueryParserDeepNesting extends LuceneTestCase {

  /** Large enough to exhaust any default thread stack (the overflow starts at a few thousand). */
  private static final int DEPTH = 100_000;

  private interface ParseCall {
    Query run() throws ParserException;
  }

  /** The contract: either a Query comes back or a ParserException reports the bad input. */
  private static void assertQueryOrParserException(String what, ParseCall call) {
    try {
      assertNotNull(call.run());
    } catch (@SuppressWarnings("unused") ParserException expected) {
      // fine: the parser rejected the input with its declared exception
    } catch (StackOverflowError e) {
      throw new AssertionError(
          what
              + ": StackOverflowError instead of a Query or the declared ParserException,"
              + " nesting depth "
              + DEPTH,
          e);
    }
  }

  /**
   * An application that builds (or already holds) the DOM passes it to the public getQuery(Element)
   * entry. No XML text is parsed, so no XML-parser element-depth limit applies on any JDK.
   */
  public void testDeeplyNestedBooleanQueryElement() throws Exception {
    Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
    Element inner = doc.createElement("TermQuery");
    inner.setAttribute("fieldName", "f");
    inner.setTextContent("a");
    // built bottom-up, so each appendChild is O(1)
    for (int i = 0; i < DEPTH; i++) {
      Element clause = doc.createElement("Clause");
      clause.setAttribute("occurs", "must");
      clause.appendChild(inner);
      Element bq = doc.createElement("BooleanQuery");
      bq.appendChild(clause);
      inner = bq;
    }
    doc.appendChild(inner);
    CoreParser parser = new CoreParser("f", new MockAnalyzer(random()));
    assertQueryOrParserException(
        "CoreParser.getQuery(Element)", () -> parser.getQuery(doc.getDocumentElement()));
  }

  /** Control: nesting itself is legal input; 40 levels parse to a BooleanQuery on every JDK. */
  public void testModeratelyNestedBooleanQueryParses() throws Exception {
    byte[] xml = nestedBooleanXml(40).getBytes(StandardCharsets.UTF_8);
    Query q = new CoreParser("f", new MockAnalyzer(random())).parse(new ByteArrayInputStream(xml));
    assertTrue(q instanceof BooleanQuery);
  }

  private static String nestedBooleanXml(int depth) {
    StringBuilder sb = new StringBuilder(depth * 64);
    for (int i = 0; i < depth; i++) {
      sb.append("<BooleanQuery><Clause occurs=\"must\">");
    }
    sb.append("<TermQuery fieldName=\"f\">a</TermQuery>");
    for (int i = 0; i < depth; i++) {
      sb.append("</Clause></BooleanQuery>");
    }
    return sb.toString();
  }

  /**
   * The same nesting as XML text through parse(InputStream). With the JDK 25 XML implementation
   * (jdk.xml.maxElementDepth=100 in conf/jaxp.properties) this is rejected as ParserException;
   * with the JDK 21 XML implementation (no element-depth limit by default), which the 10.x
   * releases support, or with another JAXP implementation on the classpath, it overflows.
   */
  public void testDeeplyNestedBooleanQueryXml() {
    byte[] xml = nestedBooleanXml(DEPTH).getBytes(StandardCharsets.UTF_8);
    CoreParser parser = new CoreParser("f", new MockAnalyzer(random()));
    assertQueryOrParserException(
        "CoreParser.parse(InputStream)", () -> parser.parse(new ByteArrayInputStream(xml)));
  }
}
