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
 * A CHECKCOMPOUNDPATTERN with a replacement and empty beginchars ("endchars beginchars/flag
 * replacement" with nothing before the slash): a word that ends with the replacement leaves an
 * empty second part, and spell() looks up a zero-length word (AssertionError with -ea). Public API
 * only.
 */
public class TestCompoundPatternEmptyTail extends LuceneTestCase {

  public void testReplacementAtWordEnd() throws Exception {
    String aff = "COMPOUNDFLAG C\nCOMPOUNDMIN 1\nCHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN o /X z\n";
    String dic = "2\nfoo/C\nbar/C\n";
    Hunspell h;
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      h = new Hunspell(new Dictionary(new ByteBuffersDirectory(), "dict", a, d));
    }
    assertTrue(h.spell("foobar"));
    assertFalse(h.spell("fozbar"));
    // "foz": the replacement z is the last letter; expanding it gives "foo" + "" (empty second part).
    // Expected: false, as C Hunspell answers. Actual with -ea: AssertionError from WordStorage.lookupWord
    assertFalse(h.spell("foz"));
  }
}
