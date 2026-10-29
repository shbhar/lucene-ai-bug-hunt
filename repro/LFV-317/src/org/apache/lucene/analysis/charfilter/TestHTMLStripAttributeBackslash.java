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
package org.apache.lucene.analysis.charfilter;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHTMLStripAttributeBackslash extends LuceneTestCase {

  public void testAttributeValueEndingInBackslash() throws Exception {
    String html = "<a href=\"C:\\Temp\\\">folder</a> text <a href=\"x\">y</a>";
    StringWriter out = new StringWriter();
    try (Reader r = new HTMLStripCharFilter(new StringReader(html))) {
      r.transferTo(out);
    }
    // expected "folder text y"; actual "y": the backslash is read as escaping the closing quote
    assertEquals("folder text y", out.toString());
  }
}
