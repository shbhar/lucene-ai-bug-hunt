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

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import org.apache.lucene.spatial.prefix.tree.DateRangePrefixTree;

/**
 * Print-only sweep (public API): for both shipped template calendars, parse every "YYYY-MM-DD"
 * with day 1..32 for 14 years and classify each string against the calendar's own strict
 * (non-lenient) reading. Run on stock main and with the month-length fix shadowed in.
 */
public class DateRangeDaySweep {
  static final int[] YEARS = {1, 4, 100, 1500, 1582, 1600, 1900, 2000, 2013, 2014, 2016, 2024, 2100, 9999};

  /** True when the calendar itself, non-lenient, accepts the date (calendar-specific lengths). */
  static boolean realDate(Calendar template, int y, int m, int d) {
    Calendar c = (Calendar) template.clone();
    c.setLenient(false);
    c.clear();
    c.set(Calendar.ERA, 1);
    c.set(y, m - 1, d);
    try {
      c.getTimeInMillis();
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  static String outcome(DateRangePrefixTree tree, String s) {
    try {
      return tree.toString(tree.parseCalendar(s));
    } catch (ParseException e) {
      return "ParseException";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  static String shape(DateRangePrefixTree tree, String s) {
    try {
      return tree.parseShape(s).toString();
    } catch (ParseException e) {
      return "ParseException";
    } catch (RuntimeException e) {
      return e.getClass().getSimpleName() + ": " + e.getMessage();
    }
  }

  public static void main(String[] args) {
    Calendar[] templates = {DateRangePrefixTree.DEFAULT_CAL, DateRangePrefixTree.JAVA_UTIL_TIME_COMPAT_CAL};
    String[] names = {"DEFAULT_CAL", "JAVA_UTIL_TIME_COMPAT_CAL"};
    for (int t = 0; t < templates.length; t++) {
      DateRangePrefixTree tree = new DateRangePrefixTree(templates[t]);
      int strings = 0, realRoundTrip = 0, realOther = 0, impossibleRejected = 0, day32Rejected = 0;
      List<String> impossibleAccepted = new ArrayList<>();
      List<String> realNotRoundTrip = new ArrayList<>();
      for (int y : YEARS) {
        for (int m = 1; m <= 12; m++) {
          for (int d = 1; d <= 32; d++) {
            String s = String.format("%04d-%02d-%02d", y, m, d);
            String got = outcome(tree, s);
            strings++;
            if (d == 32) {
              if (got.equals("ParseException")) day32Rejected++;
              else impossibleAccepted.add(s + " -> " + got);
            } else if (realDate(templates[t], y, m, d)) {
              if (got.equals(s)) realRoundTrip++;
              else {
                realOther++;
                realNotRoundTrip.add(s + " -> " + got);
              }
            } else if (got.equals("ParseException")) {
              impossibleRejected++;
            } else {
              impossibleAccepted.add(s + " -> " + got);
            }
          }
        }
      }
      System.out.println("== " + names[t] + " ==");
      System.out.println(
          "strings="
              + strings
              + " real_roundtrip="
              + realRoundTrip
              + " real_not_roundtrip="
              + realOther
              + " day32_rejected="
              + day32Rejected
              + " impossible_rejected="
              + impossibleRejected
              + " impossible_accepted="
              + impossibleAccepted.size());
      System.out.println("impossible_accepted: " + impossibleAccepted);
      System.out.println("real_not_roundtrip: " + realNotRoundTrip);
      for (String s :
          new String[] {
            "2014-11-31", "2014-02-30", "2013-02-29", "2014-04-31T10", "2016-02-29", "1500-02-29",
            "1582-10-10", "2014-11-30T24", "2014-12-31T24", "2014-11-30T23:59:59.999"
          }) {
        System.out.println("  parseCalendar(" + s + ") -> " + outcome(tree, s));
      }
      for (String s :
          new String[] {
            "[2014-11-01 TO 2014-11-31]", "[2014-11-01 TO 2014-11-30]", "[2014-02-01 TO 2014-02-31]",
            "[2014-11-30T00 TO 2014-11-30T24]", "[* TO 2014-11-30]"
          }) {
        System.out.println("  parseShape(" + s + ") -> " + shape(tree, s));
      }
    }
  }
}
