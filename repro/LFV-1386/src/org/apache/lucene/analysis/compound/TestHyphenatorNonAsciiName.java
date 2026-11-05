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
package org.apache.lucene.analysis.compound;

import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestHyphenatorNonAsciiName extends LuceneTestCase {

  public void testNonAsciiHyphenatorName() throws Exception {
    Path dir = createTempDir();
    Files.writeString(
        dir.resolve("hyph_ø.xml"),
        "<!DOCTYPE hyphenation-info SYSTEM \"hyphenation.dtd\">\n"
            + "<hyphenation-info><patterns>a1b</patterns></hyphenation-info>\n");
    // expected: the analyzer builds (as it does for "hyph_o.xml");
    // actual: MalformedURIException "Path contains invalid character: ø"
    CustomAnalyzer.builder(dir)
        .withTokenizer("whitespace")
        .addTokenFilter("hyphenationCompoundWord", "hyphenator", "hyph_ø.xml")
        .build()
        .close();
  }
}
