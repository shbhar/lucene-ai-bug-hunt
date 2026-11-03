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
import java.io.InputStream;
import java.nio.file.Path;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * monitor-index-10.2.2.zip holds a persistent Monitor query index written by Lucene 10.2.2 (with
 * -Dtests.seed pinned on the writer JVM) with one registered query, q = "f:alpha OR f:beta", and
 * the same parser as below.
 */
public class TestMonitorIndexFromOlderRelease extends LuceneTestCase {

  static Query parse(String s) {
    BooleanQuery.Builder b = new BooleanQuery.Builder();
    for (String t : s.split(" ")) {
      b.add(new TermQuery(new Term("f", t)), BooleanClause.Occur.SHOULD);
    }
    return b.build();
  }

  public void testQueryIndexWrittenBy10_2_2StillMatches() throws IOException {
    Path indexPath = createTempDir("monitor");
    try (InputStream in = getClass().getResourceAsStream("monitor-index-10.2.2.zip")) {
      TestUtil.unzip(in, indexPath);
    }
    MonitorConfiguration config =
        new MonitorConfiguration()
            .setIndexPath(
                indexPath,
                MonitorQuerySerializer.fromParser(TestMonitorIndexFromOlderRelease::parse));
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), config)) {
      assertEquals(1, monitor.getQueryCount());
      Document doc = new Document();
      doc.add(new TextField("f", "alpha", Field.Store.NO));
      assertEquals(1, monitor.match(doc, QueryMatch.SIMPLE_MATCHER).getMatchCount());
    }
  }
}
