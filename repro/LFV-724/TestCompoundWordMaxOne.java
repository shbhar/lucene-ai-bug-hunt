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

/** hunspell(5) COMPOUNDWORDMAX: "Set maximum word count in a compound word." Public API only. */
public class TestCompoundWordMaxOne extends LuceneTestCase {

  private static Hunspell load(int max) throws Exception {
    String aff = "COMPOUNDFLAG C\nCOMPOUNDMIN 1\nCOMPOUNDWORDMAX " + max + "\n";
    String dic = "2\nfoo/C\nbar/C\n";
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(new ByteBuffersDirectory(), "dict", a, d));
    }
  }

  public void testMaxTwoAndOne() throws Exception {
    Hunspell two = load(2);
    assertTrue(two.spell("foobar"));
    assertFalse(two.spell("foobarfoo")); // 3 words > 2: rejected, as documented
    Hunspell one = load(1);
    assertTrue(one.spell("foo"));
    // 2 words > 1: expected rejected, as C Hunspell does (fails: true)
    assertFalse("COMPOUNDWORDMAX 1, two-word compound foobar", one.spell("foobar"));
  }
}
