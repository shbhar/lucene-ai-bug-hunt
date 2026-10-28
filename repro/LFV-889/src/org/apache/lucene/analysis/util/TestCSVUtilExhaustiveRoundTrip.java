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
package org.apache.lucene.analysis.util;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Bounded-exhaustive round trip: every string over {a, comma, quote} of length 0..7 is encoded
 * with quoteEscape, placed as the FIRST of two fields, parsed, and compared. Counts mismatches by
 * shape (comma+quote, quote-only, comma-only, plain).
 */
public class TestCSVUtilExhaustiveRoundTrip extends LuceneTestCase {

  public void testAllShortValues() {
    char[] alphabet = {'a', ',', '"'};
    int total = 0;
    int[] bad = new int[4]; // 0 plain, 1 comma-only, 2 quote-only, 3 comma+quote
    int[] seen = new int[4];
    List<String> examples = new ArrayList<>();
    for (int len = 0; len <= 7; len++) {
      int n = (int) Math.pow(3, len);
      for (int code = 0; code < n; code++) {
        StringBuilder sb = new StringBuilder();
        int c = code;
        for (int i = 0; i < len; i++) {
          sb.append(alphabet[c % 3]);
          c /= 3;
        }
        String v = sb.toString();
        int shape = (v.indexOf(',') >= 0 ? 1 : 0) + (v.indexOf('"') >= 0 ? 2 : 0);
        seen[shape]++;
        total++;
        String[] f = CSVUtil.parse(CSVUtil.quoteEscape(v) + ",tail");
        if (f.length != 2 || !v.equals(f[0]) || !"tail".equals(f[1])) {
          bad[shape]++;
          if (examples.size() < 5) {
            examples.add("[" + v + "] -> " + (f.length == 0 ? "<empty array>" : "[" + f[0] + "]"));
          }
        }
      }
    }
    String summary =
        "total="
            + total
            + " mismatches: plain="
            + bad[0]
            + "/"
            + seen[0]
            + " commaOnly="
            + bad[1]
            + "/"
            + seen[1]
            + " quoteOnly="
            + bad[2]
            + "/"
            + seen[2]
            + " commaAndQuote="
            + bad[3]
            + "/"
            + seen[3]
            + " examples="
            + examples;
    System.out.println(summary);
    assertEquals(summary, 0, bad[0] + bad[1] + bad[2] + bad[3]);
  }
}
