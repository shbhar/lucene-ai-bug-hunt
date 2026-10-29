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

import java.text.ParseException;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.search.TermRangeQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DateTools says its strings sort lexicographically in date order and that stringToTime turns a
 * string produced by timeToString back into the time. Both hold for the years 1 to 9999 and fail
 * for a year before AD 1 or after 9999.
 */
public class TestDateToolsYearRange extends LuceneTestCase {

  /** Midnight GMT of a day, on the calendar DateTools itself uses (GregorianCalendar). */
  private static long gmt(int era, int yearOfEra, int month, int day) {
    GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.ROOT);
    cal.clear();
    cal.set(Calendar.ERA, era);
    cal.set(yearOfEra, month, day);
    return cal.getTimeInMillis();
  }

  private static final int AD = GregorianCalendar.AD;
  private static final int BC = GregorianCalendar.BC;
  private static final long TWO_BC = gmt(BC, 2, Calendar.JANUARY, 1);
  private static final long AD_1 = gmt(AD, 1, Calendar.JANUARY, 1);
  private static final long AD_2026 = gmt(AD, 2026, Calendar.JANUARY, 1);
  private static final long AD_10000 = gmt(AD, 10000, Calendar.JANUARY, 1);

  private static String day(long time) {
    return DateTools.timeToString(time, DateTools.Resolution.DAY);
  }

  /** 2 BC is before AD 1, so its string must sort first. */
  public void testTwoBCSortsBeforeAD1() {
    assertTrue(TWO_BC < AD_1);
    String bc = day(TWO_BC);
    String ad = day(AD_1);
    assertTrue(bc + " (2 BC) must sort before " + ad + " (AD 1)", bc.compareTo(ad) < 0);
  }

  /** The string of 2 BC-01-01 must read back as 2 BC-01-01. */
  public void testTwoBCRoundTrip() throws ParseException {
    String s = day(TWO_BC);
    assertEquals(s + " must read back as 2 BC-01-01", TWO_BC, DateTools.stringToTime(s));
  }

  /** Year 10000 is after 2026, so its string must sort last. */
  public void testYear10000SortsAfter2026() {
    String now = day(AD_2026);
    String later = day(AD_10000);
    assertTrue(now + " (2026) must sort before " + later + " (10000)", now.compareTo(later) < 0);
  }

  /** The string of 10000-01-01 must read back as 10000-01-01. */
  public void testYear10000RoundTrip() throws ParseException {
    String s = day(AD_10000);
    assertEquals(s + " must read back as 10000-01-01", AD_10000, DateTools.stringToTime(s));
  }

  /** A range query for AD 1000 to AD 1500 must not match a document dated 1200 BC. */
  public void testRangeQueryDoesNotMatchBCDate() throws Exception {
    String bc1200 = day(gmt(BC, 1200, Calendar.JUNE, 1));
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new StringField("date", bc1200, Field.Store.NO));
      w.addDocument(doc);
      try (DirectoryReader r = DirectoryReader.open(w)) {
        String from = day(gmt(AD, 1000, Calendar.JANUARY, 1));
        String to = day(gmt(AD, 1500, Calendar.JANUARY, 1));
        TermRangeQuery q = TermRangeQuery.newStringRange("date", from, to, true, true);
        assertEquals(
            "1200 BC, indexed as " + bc1200 + ", matched " + q, 0, newSearcher(r).count(q));
      }
    }
  }

  /** Control (passes today): inside the years 1 to 9999 both promises hold at every resolution. */
  public void testControlYears1To9999() throws ParseException {
    long[] times = {
      AD_1,
      gmt(AD, 1582, Calendar.OCTOBER, 15),
      0L,
      AD_2026,
      gmt(AD, 9999, Calendar.DECEMBER, 31) + 86_399_999L // 9999-12-31 23:59:59.999
    };
    for (DateTools.Resolution r : DateTools.Resolution.values()) {
      String previous = null;
      for (long t : times) {
        String s = DateTools.timeToString(t, r);
        assertEquals(s, DateTools.round(t, r), DateTools.stringToTime(s));
        if (previous != null) {
          assertTrue(previous + " must sort before " + s, previous.compareTo(s) < 0);
        }
        previous = s;
      }
    }
  }
}
