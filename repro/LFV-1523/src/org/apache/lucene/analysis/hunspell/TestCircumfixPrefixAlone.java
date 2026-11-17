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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCircumfixPrefixAlone extends LuceneTestCase {

  // expected: neither half of a CIRCUMFIX pair is a word alone; actual: spell("prestem") is true
  public void testCircumfixPrefixNeedsItsSuffix() throws Exception {
    String aff =
        """
        CIRCUMFIX X
        PFX P Y 1
        PFX P 0 pre/X .
        SFX S Y 1
        SFX S 0 suf/X .
        """;
    String dic =
        """
        1
        stem/PS
        """;
    Hunspell hunspell =
        new Hunspell(new Dictionary(new ByteBuffersDirectory(), "tmp", stream(aff), stream(dic)));

    assertTrue(hunspell.spell("prestemsuf"));
    assertFalse("the suffix without its prefix", hunspell.spell("stemsuf"));
    assertFalse("the prefix without its suffix", hunspell.spell("prestem"));
  }

  private static InputStream stream(String text) {
    return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
  }
}
