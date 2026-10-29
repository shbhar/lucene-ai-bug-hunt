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
package org.apache.lucene.queryparser;

import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** The /regexp/ syntax of the classic and flexible parsers builds the same RegexpQuery. */
public class TestRegexpNullableRunParsers extends LuceneTestCase {
  private static final int N = 1000;
  private static final String QUERY = "/a" + ".*".repeat(N) + "b/";

  private static void check(String parser, Query q) {
    assertTrue(parser + " returned " + q.getClass().getSimpleName(), q instanceof RegexpQuery);
    long t = ((RegexpQuery) q).getAutomaton().getNumTransitions();
    assertTrue(parser + " " + "/a(.*)^" + N + "b/ -> RegexpQuery with " + t + " transitions", t <= 10L * N);
  }

  public void testClassicQueryParser() throws Exception {
    check("classic QueryParser", new QueryParser("f", new MockAnalyzer(random())).parse(QUERY));
  }

  public void testStandardQueryParser() throws Exception {
    StandardQueryParser p = new StandardQueryParser(new MockAnalyzer(random()));
    check("flexible StandardQueryParser", p.parse(QUERY, "f"));
  }
}
