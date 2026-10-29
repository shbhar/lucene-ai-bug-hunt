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
package org.apache.lucene.monitor;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRegisterDuplicateIdInOneCall extends LuceneTestCase {

  public void testOneBatchBehavesLikeOneOfTheVersions() throws Exception {
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      Query appleOrBanana =
          new BooleanQuery.Builder()
              .add(new TermQuery(new Term("f", "apple")), Occur.SHOULD)
              .add(new TermQuery(new Term("f", "banana")), Occur.SHOULD)
              .build();
      Query cherry = new TermQuery(new Term("f", "cherry"));
      monitor.register(
          List.of(new MonitorQuery("q", appleOrBanana), new MonitorQuery("q", cherry)));
      Set<String> matched = new TreeSet<>();
      for (String text : List.of("apple", "banana", "cherry")) {
        Document doc = new Document();
        doc.add(new TextField("f", text, Field.Store.NO));
        if (monitor.match(doc, QueryMatch.SIMPLE_MATCHER).matches("q") != null) {
          matched.add(text);
        }
      }
      // expected one version, [apple, banana] or [cherry]; actual [banana, cherry]
      assertTrue(
          "q matches " + matched + ", expected [apple, banana] or [cherry]",
          matched.equals(Set.of("apple", "banana")) || matched.equals(Set.of("cherry")));
    }
  }
}
