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
package org.apache.lucene.analysis.core;

import java.io.StringReader;
import javax.xml.parsers.DocumentBuilderFactory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

public class TestStopFilterFactoryJavadocExample extends LuceneTestCase {

  // StopFilterFactory javadoc example, verbatim: expected well-formed XML; the filter tag never closes
  private static final String EXAMPLE =
      """
      <fieldType name="text_stop" class="solr.TextField" positionIncrementGap="100" autoGeneratePhraseQueries="true">
        <analyzer>
          <tokenizer class="solr.WhitespaceTokenizerFactory"/>
          <filter class="solr.StopFilterFactory" ignoreCase="true"
                  words="stopwords.txt" format="wordset"
        </analyzer>
      </fieldType>
      """;

  public void testExampleIsWellFormedXml() throws Exception {
    DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
    dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    try {
      dbf.newDocumentBuilder().parse(new InputSource(new StringReader(EXAMPLE)));
    } catch (SAXParseException e) {
      throw new AssertionError("expected well-formed XML, but: " + e.getMessage(), e);
    }
  }
}
