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
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestComplexPrefixesSecondPrefix extends LuceneTestCase {

  public void testSecondPrefixNeedsContinuationClass() throws Exception {
    String aff =
        """
        COMPLEXPREFIXES
        PFX A Y 1
        PFX A 0 re .
        PFX C Y 2
        PFX C 0 un .
        PFX C 0 dis/A .
        """;
    Dictionary dictionary =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream("1\nfoo/C\n".getBytes(StandardCharsets.UTF_8)));
    // "re" (A) may only precede "dis/A", not "un": expected spell("reunfoo") false, actual true
    assertFalse(
        "spell(\"reunfoo\") expected false, was true", new Hunspell(dictionary).spell("reunfoo"));
  }
}
