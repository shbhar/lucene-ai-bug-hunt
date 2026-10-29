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
import java.time.Instant;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check of two DateTools contracts:
 *
 * <ol>
 *   <li>class javadoc: "The strings are structured so that lexicographic sorting orders them by
 *       date";
 *   <li>stringToTime: "Converts a string produced by timeToString ... back to a time".
 * </ol>
 *
 * Only public API is used (DateTools.timeToString, stringToTime, round). Instants are built with
 * the same calendar system DateTools uses internally (GregorianCalendar, GMT), and cross-checked
 * with java.time so the ordering of the inputs is not in doubt.
 */
public class TestDateToolsYearRangeV1 extends LuceneTestCase {

  private static long gmt(int era, int yearOfEra, int month0, int day) {
    GregorianCalendar cal = new GregorianCalendar(TimeZone.getTimeZone("GMT"), Locale.ROOT);
    cal.clear();
    cal.set(Calendar.ERA, era);
    cal.set(Calendar.YEAR, yearOfEra);
    cal.set(Calendar.MONTH, month0);
    cal.set(Calendar.DAY_OF_MONTH, day);
    return cal.getTimeInMillis();
  }

  private static void assertSortsBefore(
      long earlier, long later, DateTools.Resolution r, String what) {
    assertTrue("inputs must be ordered: " + what, earlier < later);
    assertTrue(
        "rounded instants must differ: " + what,
        DateTools.round(earlier, r) < DateTools.round(later, r));
    String a = DateTools.timeToString(earlier, r);
    String b = DateTools.timeToString(later, r);
    assertTrue(
        what + ": expected \"" + a + "\" to sort before \"" + b + "\" (compareTo=" + a.compareTo(b)
            + ")",
        a.compareTo(b) < 0);
  }

  private static void assertRoundTrips(long t, DateTools.Resolution r, String what)
      throws ParseException {
    String s = DateTools.timeToString(t, r);
    long back;
    try {
      back = DateTools.stringToTime(s);
    } catch (ParseException e) {
      throw new AssertionError(
          what + ": stringToTime threw ParseException on timeToString's own output \"" + s + "\"",
          e);
    }
    assertEquals(
        what + ": string \"" + s + "\" did not parse back to round(t)",
        DateTools.round(t, r),
        back);
  }

  /** Control: the oracle holds for years 1..9999 (this test is expected to pass). */
  public void testControlFourDigitYears() throws ParseException {
    long[] ts = {
      gmt(GregorianCalendar.AD, 1, 0, 1),
      gmt(GregorianCalendar.AD, 1582, 9, 4), // last Julian day
      gmt(GregorianCalendar.AD, 1582, 9, 15), // first Gregorian day
      gmt(GregorianCalendar.AD, 1969, 11, 31),
      gmt(GregorianCalendar.AD, 1970, 0, 1),
      gmt(GregorianCalendar.AD, 2026, 9, 26),
      gmt(GregorianCalendar.AD, 9999, 11, 31)
    };
    for (DateTools.Resolution r :
        new DateTools.Resolution[] {DateTools.Resolution.DAY, DateTools.Resolution.MILLISECOND}) {
      for (int i = 0; i + 1 < ts.length; i++) {
        assertSortsBefore(ts[i], ts[i + 1], r, "control " + i + " " + r);
      }
      for (long t : ts) {
        assertRoundTrips(t, r, "control " + t + " " + r);
      }
    }
  }

  /** 2 BC-01-01 is earlier than AD 1-01-01, so its string must sort first. */
  public void testBCDateSortsBeforeAD() {
    long twoBC = gmt(GregorianCalendar.BC, 2, 0, 1);
    long oneAD = gmt(GregorianCalendar.AD, 1, 0, 1);
    // cross-check with java.time (proleptic ISO: year -1 == 2 BC); calendars differ by ~2 days
    // at this epoch but the order is the same
    assertTrue(Instant.parse("-0001-01-01T00:00:00Z").toEpochMilli() < oneAD);
    assertSortsBefore(twoBC, oneAD, DateTools.Resolution.DAY, "2 BC vs AD 1");
  }

  /** 1 BC and AD 1 are different years; at YEAR resolution they must not share a string. */
  public void testOneBCAndOneADDistinctYearStrings() {
    long oneBC = gmt(GregorianCalendar.BC, 1, 0, 1);
    long oneAD = gmt(GregorianCalendar.AD, 1, 0, 1);
    assertSortsBefore(oneBC, oneAD, DateTools.Resolution.YEAR, "1 BC vs AD 1 (YEAR)");
  }

  /** A string produced by timeToString for a BC date must parse back to the same instant. */
  public void testBCDateRoundTrip() throws ParseException {
    long twoBC = gmt(GregorianCalendar.BC, 2, 0, 1);
    assertRoundTrips(twoBC, DateTools.Resolution.DAY, "2 BC-01-01 DAY");
  }

  /** AD 10000-01-01 is later than 2026-10-26, so its string must sort after it. */
  public void testYear10000SortsAfter2026() {
    long y2026 = gmt(GregorianCalendar.AD, 2026, 9, 26);
    long y10000 = gmt(GregorianCalendar.AD, 10000, 0, 1);
    assertEquals(Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli(), y10000);
    assertSortsBefore(y2026, y10000, DateTools.Resolution.DAY, "2026 vs 10000");
  }

  /** A string produced by timeToString for year 10000 must parse back. */
  public void testYear10000RoundTrip() throws ParseException {
    long y10000 = gmt(GregorianCalendar.AD, 10000, 0, 1);
    assertRoundTrips(y10000, DateTools.Resolution.DAY, "10000-01-01 DAY");
  }
}
