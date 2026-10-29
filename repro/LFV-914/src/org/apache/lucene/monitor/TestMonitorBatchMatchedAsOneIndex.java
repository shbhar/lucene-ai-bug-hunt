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

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.FuzzyQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMonitorBatchMatchedAsOneIndex extends LuceneTestCase {

  private static Document doc(String text) {
    Document d = new Document();
    d.add(new TextField("f", text, Field.Store.NO));
    return d;
  }

  public void testBatchResultDependsOnOtherDocuments() throws Exception {
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(
          new MonitorQuery("q", new FuzzyQuery(new Term("f", "abcdef"), 2, 0, 1, true)));
      Document far = doc("abcdxy");
      boolean alone = monitor.match(far, QueryMatch.SIMPLE_MATCHER).matches("q") != null;
      Document[] batch = {far, doc("abcdeg")};
      boolean inBatch = monitor.match(batch, QueryMatch.SIMPLE_MATCHER).matches("q", 0) != null;
      // expected: doc 0 matches alone and in the batch; actual: the batch's closer abcdeg wins
      assertEquals("abcdxy matches q alone, so also in a batch", alone, inBatch);
    }
  }
}
