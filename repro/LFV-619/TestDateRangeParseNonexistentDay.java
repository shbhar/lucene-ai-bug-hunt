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
package org.apache.lucene.spatial.prefix.tree;

import java.text.ParseException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * DateRangePrefixTree.parseCalendar rejects a day-of-month above 31 (and a month above 12, an hour
 * above 24, ...), but a day that does not exist in its month (Feb 30, Nov 31, Feb 29 of a common
 * year) is accepted and silently rolled over into the next month, so a range such as
 * [2014-11-01 TO 2014-11-31] also covers December 1st.
 */
public class TestDateRangeParseNonexistentDay extends LuceneTestCase {

  public void testNonexistentDayOfMonthIsRejected() throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.JAVA_UTIL_TIME_COMPAT_CAL);
    // control: the parser validates the day-of-month range
    expectThrows(ParseException.class, () -> tree.parseCalendar("2014-11-32"));

    List<String> accepted = new ArrayList<>();
    for (String s : new String[] {"2014-02-30", "2014-11-31", "2013-02-29", "2014-04-31T10"}) {
      // ISO-8601 (java.time, whose ISO_INSTANT format toString(Calendar) mirrors) has no such date
      expectThrows(DateTimeException.class, () -> LocalDate.parse(s.substring(0, 10)));
      try {
        accepted.add(s + " -> " + tree.toString(tree.parseCalendar(s)));
      } catch (ParseException expected) {
        // correct behaviour
      }
    }
    if (accepted.isEmpty() == false) {
      fail(
          "non-existent dates accepted: "
              + accepted
              + "; e.g. [2014-11-01 TO 2014-11-31] parses to "
              + tree.parseShape("[2014-11-01 TO 2014-11-31]"));
    }
  }
}
