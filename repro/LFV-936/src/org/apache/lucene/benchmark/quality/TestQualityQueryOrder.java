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
package org.apache.lucene.benchmark.quality;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import org.apache.lucene.benchmark.quality.trec.TrecTopicsReader;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * QualityQuery implements Comparable (the topic readers sort their result with it), so compareTo
 * must be a total order. It compares two numeric ids as ints and anything else as strings, which is
 * not transitive once numeric and non-numeric ids are mixed.
 */
public class TestQualityQueryOrder extends LuceneTestCase {

  private static QualityQuery q(String id) {
    return new QualityQuery(id, Map.of());
  }

  public void testCompareToIsTransitive() {
    QualityQuery two = q("2"), hundred = q("100"), oneA = q("1a");
    assertTrue("2 < 100 (compared as ints)", two.compareTo(hundred) < 0);
    assertTrue("100 < 1a (compared as strings)", hundred.compareTo(oneA) < 0);
    // transitivity requires 2 < 1a, but as strings "1a" < "2"
    assertTrue("2 < 100 and 100 < 1a, so 2 must be < 1a", two.compareTo(oneA) < 0);
  }

  /**
   * A topics file in natural order 1..111 where four topics have a variant with an "a" suffix
   * (1a, 20a, 64a, 99a), each listed right after its parent topic.
   */
  public void testTrecTopicsReaderWithSuffixedTopicIds() throws Exception {
    StringBuilder topics = new StringBuilder();
    int count = 0;
    for (int i = 1; i <= 111; i++) {
      count += addTopic(topics, Integer.toString(i));
      if (i == 1 || i == 20 || i == 64 || i == 99) {
        count += addTopic(topics, i + "a");
      }
    }
    QualityQuery[] qqs =
        new TrecTopicsReader().readQueries(new BufferedReader(new StringReader(topics.toString())));
    assertEquals(count, qqs.length);
    for (int i = 1; i < qqs.length; i++) {
      assertTrue(
          "not sorted: " + qqs[i - 1].getQueryID() + " before " + qqs[i].getQueryID(),
          qqs[i - 1].compareTo(qqs[i]) <= 0);
    }
  }

  private static int addTopic(StringBuilder sb, String id) {
    sb.append("<top>\n<num> Number: ")
        .append(id)
        .append("\n\n<title> topic ")
        .append(id)
        .append("\n\n<desc> Description:\nd\n\n<narr> Narrative:\nn\n\n</top>\n\n");
    return 1;
  }
}
