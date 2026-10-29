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
package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestWordFormsPrefixEnabledSuffix extends LuceneTestCase {

  public void testSuffixEnabledByPrefixIsGenerated() throws Exception {
    String aff = "PFX U Y 1\nPFX U 0 kilo/S .\nSFX S Y 1\nSFX S 0 s .\n";
    Dictionary dictionary =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream("1\nampere/U\n".getBytes(StandardCharsets.UTF_8)));
    List<String> forms =
        new Hunspell(dictionary)
            .getAllWordForms("ampere").stream().map(AffixedWord::getWord).sorted().toList();
    // expected kiloamperes too (the prefix kilo/S allows the suffix s); actual [ampere, kiloampere]
    assertEquals(List.of("ampere", "kiloampere", "kiloamperes"), forms);
  }
}
