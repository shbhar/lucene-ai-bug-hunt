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
package org.apache.lucene.queryparser.classic;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.document.DateTools;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QueryParserBase#getRangeQuery parses the dates of d:[6/1/2005 TO 6/4/2005] with a DateFormat in the
 * JVM default time zone, but rounds the inclusive upper date to 23:59:59.999 in the time zone set
 * with setTimeZone. When the two differ, the range is shifted: the first minute of June 1 or the
 * last minute of June 4 (in the configured zone) is left out.
 */
public class TestDateRangeTimeZone extends LuceneTestCase {

  private static long at(TimeZone tz, int day, int hour, int minute) {
    Calendar c = new GregorianCalendar(tz, Locale.ROOT);
    c.clear();
    c.set(2005, Calendar.JUNE, day, hour, minute, 0);
    return c.getTimeInMillis();
  }

  /** Control: configured zone == JVM default zone, both ends are inside the range. */
  public void testDefaultTimeZone() throws Exception {
    checkBothEndsMatch(TimeZone.getDefault());
  }

  public void testConfiguredTimeZoneIsUsedForBothEnds() throws Exception {
    // a zone whose offset differs from the JVM default zone on these dates
    TimeZone zone = TimeZone.getTimeZone("Asia/Tokyo");
    long probe = at(TimeZone.getTimeZone("UTC"), 1, 0, 0);
    if (zone.getOffset(probe) == TimeZone.getDefault().getOffset(probe)) {
      zone = TimeZone.getTimeZone("America/Los_Angeles");
    }
    assertTrue(zone.getOffset(probe) != TimeZone.getDefault().getOffset(probe));
    checkBothEndsMatch(zone);
  }

  private void checkBothEndsMatch(TimeZone zone) throws Exception {
    long firstMinute = at(zone, 1, 0, 1); // June 1, 00:01 in the configured zone
    long lastMinute = at(zone, 4, 23, 58); // June 4, 23:58 in the configured zone

    Directory dir = newDirectory();
    try (RandomIndexWriter w =
        new RandomIndexWriter(random(), dir, new IndexWriterConfig(new KeywordAnalyzer()))) {
      for (long t : new long[] {firstMinute, lastMinute}) {
        Document d = new Document();
        d.add(new StringField("d", DateTools.timeToString(t, DateTools.Resolution.MINUTE), Field.Store.YES));
        w.addDocument(d);
      }
    }

    QueryParser qp = new QueryParser("d", new KeywordAnalyzer());
    qp.setLocale(Locale.US);
    qp.setTimeZone(zone);
    qp.setDateResolution(DateTools.Resolution.MINUTE);
    Query q = qp.parse("d:[6/1/2005 TO 6/4/2005]");
    try (IndexReader r = DirectoryReader.open(dir)) {
      IndexSearcher s = newSearcher(r);
      TermRangeQuery range = (TermRangeQuery) q;
      String bounds =
          DateTools.stringToDate(range.getLowerTerm().utf8ToString()).toInstant()
              + " .. "
              + DateTools.stringToDate(range.getUpperTerm().utf8ToString()).toInstant();
      assertEquals(
          "range " + bounds + " for zone " + zone.getID() + " (JVM default "
              + TimeZone.getDefault().getID() + ")",
          2,
          s.count(q));
    }
    dir.close();
  }
}
