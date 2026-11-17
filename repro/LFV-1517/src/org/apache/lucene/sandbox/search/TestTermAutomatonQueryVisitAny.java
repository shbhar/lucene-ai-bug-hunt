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
package org.apache.lucene.sandbox.search;

import java.util.HashSet;
import java.util.Set;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTermAutomatonQueryVisitAny extends LuceneTestCase {

  public void testVisitWithAnyTransition() {
    TermAutomatonQuery q = new TermAutomatonQuery("field");
    int s0 = q.createState();
    int s1 = q.createState();
    int s2 = q.createState();
    int s3 = q.createState();
    q.addTransition(s0, s1, "a");
    q.addAnyTransition(s1, s2);
    q.addTransition(s2, s3, "b");
    q.setAccept(s3, true);
    q.finish();
    Set<Term> terms = new HashSet<>();
    q.visit(QueryVisitor.termCollector(terms));
    for (Term term : terms) {
      assertNotNull("expected the terms a and b only, got a Term with null bytes", term.bytes());
    }
  }
}
