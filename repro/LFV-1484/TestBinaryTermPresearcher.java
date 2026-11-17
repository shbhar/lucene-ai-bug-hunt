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

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestBinaryTermPresearcher extends LuceneTestCase {

  // Expected: 1 match, the document contains the query's term; actual: 0 matches and no error.
  public void testBinaryTermQueryMatchesWithDefaultPresearcher() throws IOException {
    BytesRef id = new BytesRef(new byte[] {(byte) 0xC8});
    try (Monitor monitor = new Monitor(new StandardAnalyzer())) {
      monitor.register(new MonitorQuery("q", new TermQuery(new Term("id", id))));

      Document doc = new Document();
      doc.add(new StringField("id", id, Field.Store.NO));
      doc.add(new StringField("tag", "abc", Field.Store.NO));

      assertEquals(1, monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}
