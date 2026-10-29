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

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DateTools: "The strings are structured so that lexicographic sorting orders them by date". The
 * year is formatted with the year-of-era pattern "yyyy", so a year before AD 1 loses its era and a
 * year after 9999 gets a fifth digit.
 */
public class TestDateToolsOrderOutsideFourDigitYears extends LuceneTestCase {

  private static long gmt(int era, int year, int month, int day) {
    GregorianCalendar c = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.ROOT);
    c.clear();
    c.set(Calendar.ERA, era);
    c.set(year, month, day);
    return c.getTimeInMillis();
  }

  /** 2 BC is before AD 1, but its string "0002" sorts after "0001". */
  public void testYearBeforeAD1() throws Exception {
    long twoBC = gmt(GregorianCalendar.BC, 2, Calendar.JANUARY, 1);
    long ad1 = gmt(GregorianCalendar.AD, 1, Calendar.JANUARY, 1);
    assertTrue(twoBC < ad1);
    String s1 = DateTools.timeToString(twoBC, DateTools.Resolution.DAY);
    String s2 = DateTools.timeToString(ad1, DateTools.Resolution.DAY);
    assertTrue(s1 + " must sort before " + s2, s1.compareTo(s2) < 0);
  }

  /** The string of a BC date parses back to the AD date with the same year number. */
  public void testYearBeforeAD1RoundTrip() throws Exception {
    long twoBC = gmt(GregorianCalendar.BC, 2, Calendar.JANUARY, 1);
    String s = DateTools.timeToString(twoBC, DateTools.Resolution.DAY);
    assertEquals(s, twoBC, DateTools.stringToTime(s));
  }

  /** Year 10000 is after 2026, but its string "100000101" sorts before "20260101". */
  public void testYearAfter9999() throws Exception {
    long y2026 = gmt(GregorianCalendar.AD, 2026, Calendar.JANUARY, 1);
    long y10000 = gmt(GregorianCalendar.AD, 10000, Calendar.JANUARY, 1);
    String s1 = DateTools.timeToString(y2026, DateTools.Resolution.DAY);
    String s2 = DateTools.timeToString(y10000, DateTools.Resolution.DAY);
    assertTrue(s1 + " must sort before " + s2, s1.compareTo(s2) < 0);
  }
}
