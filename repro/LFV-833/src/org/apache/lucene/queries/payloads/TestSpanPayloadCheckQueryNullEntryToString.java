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
package org.apache.lucene.queries.payloads;

import java.util.Collections;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.spans.SpanTermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSpanPayloadCheckQueryNullEntryToString extends LuceneTestCase {

  public void testToStringWithNullEntry() {
    SpanPayloadCheckQuery q =
        new SpanPayloadCheckQuery(
            new SpanTermQuery(new Term("f", "a")), Collections.singletonList(null));
    try {
      q.toString();
    } catch (NullPointerException e) {
      // null = 'no payload here' to the matcher; expected: a query string, actual: NPE
      throw new AssertionError("expected toString() to describe the query, got " + e, e);
    }
  }
}
