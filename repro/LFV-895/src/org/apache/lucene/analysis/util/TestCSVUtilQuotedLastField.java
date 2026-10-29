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

import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCSVUtilQuotedLastField extends LuceneTestCase {

  public void testQuotedLastFieldIsUnquoted() {
    // expected [x, bar]; actual [x, "bar"]: the first field is unquoted, the last keeps its quotes
    assertArrayEquals(new String[] {"x", "bar"}, CSVUtil.parse("\"x\",\"bar\""));
  }
}
