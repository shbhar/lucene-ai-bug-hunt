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
import java.util.Calendar;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDateRangeNonexistentDay extends LuceneTestCase {

  public void testNovember31IsRejected() throws Exception {
    DateRangePrefixTree tree = new DateRangePrefixTree(DateRangePrefixTree.DEFAULT_CAL);
    Calendar cal;
    try {
      cal = tree.parseCalendar("2014-11-31");
    } catch (@SuppressWarnings("unused") ParseException expected) {
      return;
    }
    fail("expected ParseException for 2014-11-31 (as for 2014-11-32), got " + tree.toString(cal));
  }
}
