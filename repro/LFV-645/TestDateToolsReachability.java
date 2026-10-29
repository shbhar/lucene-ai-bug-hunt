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
package org.apache.lucene.document;

import java.time.Instant;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** V3 reachability probe: public API only, inputs produced by ordinary JDK date APIs. */
public class TestDateToolsReachability extends LuceneTestCase {

  private static long ms(String iso) {
    return Instant.parse(iso).toEpochMilli();
  }

  private static int countRange(long fromMs, long toMs, long... docMs) throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())))) {
      for (long t : docMs) {
        Document d = new Document();
        d.add(new StringField("date", DateTools.timeToString(t, DateTools.Resolution.DAY), Field.Store.NO));
        w.addDocument(d);
      }
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = newSearcher(r);
        Query q =
            TermRangeQuery.newStringRange(
                "date",
                DateTools.timeToString(fromMs, DateTools.Resolution.DAY),
                DateTools.timeToString(toMs, DateTools.Resolution.DAY),
                true,
                true);
        return s.count(q);
      }
    }
  }

  /** Control: all dates inside AD 1..9999, range [AD 1000, AD 1500] matches only AD 1200. */
  public void testControlInRange() throws Exception {
    int n =
        countRange(
            ms("1000-01-01T00:00:00Z"),
            ms("1500-01-01T00:00:00Z"),
            ms("0900-06-01T00:00:00Z"),
            ms("1200-06-01T00:00:00Z"),
            ms("2026-06-01T00:00:00Z"));
    assertEquals(1, n);
  }

  /**
   * A historical date from java.time (proleptic year -1199 = 1200 BC) is indexed; a range query
   * for AD 1000..AD 1500 must not match it.
   */
  public void testBCDocMatchesADRange() throws Exception {
    long bc = ms("-1199-06-01T00:00:00Z");
    assertTrue(bc < ms("1000-01-01T00:00:00Z"));
    String s = DateTools.timeToString(bc, DateTools.Resolution.DAY);
    int n = countRange(ms("1000-01-01T00:00:00Z"), ms("1500-01-01T00:00:00Z"), bc);
    assertEquals("1200 BC indexed as " + s + " must not match [AD 1000, AD 1500]", 0, n);
  }

  /** A far-future date (AD 10000) indexed; range [AD 2000, AD 20000] must match it. */
  public void testYear10000DocInRange() throws Exception {
    long y10k = ms("+10000-01-01T00:00:00Z");
    int n = countRange(ms("2000-01-01T00:00:00Z"), ms("+20000-01-01T00:00:00Z"), y10k, ms("2026-06-01T00:00:00Z"));
    assertEquals(2, n);
  }

  /**
   * Classic QueryParser with a date resolution: a user query whose upper bound is in year 10000.
   * The documented feature (QueryParser javadoc) converts both bounds with DateTools.
   */
  public void testClassicQueryParserUpperBoundYear10000() throws Exception {
    QueryParser qp = new QueryParser("f", new MockAnalyzer(random()));
    qp.setLocale(Locale.US);
    qp.setTimeZone(TimeZone.getTimeZone("GMT"));
    qp.setDateResolution(DateTools.Resolution.DAY);
    TimeZone saved = TimeZone.getDefault();
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("GMT"));
      Query q = qp.parse("date:[1/1/2000 TO 12/31/10000]");
      assertTrue(q.toString(), q instanceof TermRangeQuery);
      TermRangeQuery trq = (TermRangeQuery) q;
      String lo = trq.getLowerTerm().utf8ToString();
      String hi = trq.getUpperTerm().utf8ToString();
      assertTrue("lower " + lo + " must sort before upper " + hi, lo.compareTo(hi) < 0);
    } finally {
      TimeZone.setDefault(saved);
    }
  }
}
