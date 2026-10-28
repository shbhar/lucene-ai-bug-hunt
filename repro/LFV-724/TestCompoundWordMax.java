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

/**
 * hunspell(5): "COMPOUNDWORDMAX number: Set maximum word count in a compound word." For the
 * flag-based dictionaries below (no COMPOUNDSYLLABLE), C Hunspell rejects the two-word compound
 * when the limit is 0 or 1.
 */
public class TestCompoundWordMax extends LuceneTestCase {

  private static final String FLAG = "COMPOUNDFLAG C\nCOMPOUNDMIN 1\n";
  private static final String FLAG_DIC = "2\nfoo/C\nbar/C\n";
  private static final String BEGIN_END = "COMPOUNDBEGIN B\nCOMPOUNDEND E\nCOMPOUNDMIN 1\n";
  private static final String BEGIN_END_DIC = "2\nfoo/B\nbar/E\n";

  private static Hunspell load(String aff, String dic) throws Exception {
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(new ByteBuffersDirectory(), "dict", a, d));
    }
  }

  /** Control: a limit of 2 is enforced as a word count. */
  public void testLimitTwo() throws Exception {
    Hunspell flag = load(FLAG + "COMPOUNDWORDMAX 2\n", FLAG_DIC);
    assertTrue(flag.spell("foobar"));
    assertFalse(flag.spell("foobarfoo"));
    assertTrue(load(BEGIN_END + "COMPOUNDWORDMAX 2\n", BEGIN_END_DIC).spell("foobar"));
  }

  public void testLimitOne() throws Exception {
    Hunspell h = load(FLAG + "COMPOUNDWORDMAX 1\n", FLAG_DIC);
    assertTrue(h.spell("foo"));
    assertFalse(h.spell("foobarfoo")); // 3 words: rejected
    assertFalse("COMPOUNDWORDMAX 1, two-word compound foobar", h.spell("foobar"));
  }

  public void testLimitZero() throws Exception {
    Hunspell h = load(FLAG + "COMPOUNDWORDMAX 0\n", FLAG_DIC);
    assertTrue(h.spell("foo"));
    assertFalse("COMPOUNDWORDMAX 0, two-word compound foobar", h.spell("foobar"));
  }

  public void testLimitOneBeginEnd() throws Exception {
    Hunspell h = load(BEGIN_END + "COMPOUNDWORDMAX 1\n", BEGIN_END_DIC);
    assertTrue(h.spell("foo"));
    assertFalse("COMPOUNDWORDMAX 1, COMPOUNDBEGIN/END compound foobar", h.spell("foobar"));
  }
}
