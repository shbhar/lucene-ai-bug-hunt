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
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * CHECKCOMPOUNDTRIPLE: "Forbid compounding, if compound word contains triple repeating letters
 * (e.g. foo|ox or xo|oof)" (hunspell(5)). The "xo|oof" form (one letter before the boundary, two
 * after) is not detected at the second and later boundaries of a compound. Uses the content of
 * Lucene's own test dictionary checkcompoundtriple.aff/.dic and only public API.
 */
public class TestCheckCompoundTripleLaterBoundary extends LuceneTestCase {

  private static Hunspell load() throws Exception {
    String aff = "CHECKCOMPOUNDTRIPLE\nCOMPOUNDFLAG A\n";
    String dic = "4\nfoo/A\nopera/A\neel/A\nbare/A\n";
    try (InputStream a = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream d = new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))) {
      return new Hunspell(new Dictionary(a, List.of(d), false, SortingStrategy.inMemory()));
    }
  }

  /** Control (checkcompoundtriple.good/.wrong): the rule works at the first boundary. */
  public void testFirstBoundaryControl() throws Exception {
    Hunspell h = load();
    assertTrue(h.spell("operaeel"));
    assertFalse("bareeel = bare|eel has 'eee'", h.spell("bareeel"));
    assertFalse("fooopera = foo|opera has 'ooo'", h.spell("fooopera"));
  }

  /** The same 'bare|eel' boundary preceded by another part. */
  public void testSecondBoundary() throws Exception {
    Hunspell h = load();
    assertFalse("operabareeel = opera + bare|eel has 'eee'", h.spell("operabareeel"));
    assertFalse("barebareeel = bare + bare|eel has 'eee'", h.spell("barebareeel"));
  }
}
