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
package org.apache.lucene.analysis.synonym;

import java.io.StringReader;
import java.text.ParseException;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSolrSynonymParserWhitespaceOnlyLine extends LuceneTestCase {

  public void testSpacesOnlyLineIsBlank() throws Exception {
    try (WhitespaceAnalyzer analyzer = new WhitespaceAnalyzer()) {
      SolrSynonymParser parser = new SolrSynonymParser(true, true, analyzer);
      try {
        parser.parse(new StringReader("couch, sofa\n   \n"));
      } catch (ParseException e) {
        // class javadoc: "Blank lines and lines starting with '#' are comments."
        throw new AssertionError(
            "expected: line 2 (three spaces) is skipped as blank; actual: " + e + " <- " + e.getCause(),
            e);
      }
    }
  }
}
