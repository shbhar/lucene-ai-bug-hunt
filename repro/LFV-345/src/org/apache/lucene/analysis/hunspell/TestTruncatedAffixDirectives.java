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
import java.text.ParseException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * An affix file whose table directive announces N entry lines but ends after fewer lines must be
 * rejected with the documented ParseException, as the PFX/SFX tables already do ("Premature end of
 * rules"). Only the public Dictionary constructor is used.
 */
public class TestTruncatedAffixDirectives extends LuceneTestCase {

  private static Dictionary load(String aff) throws Exception {
    try (Directory dir = newDirectory();
        InputStream affix = new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8));
        InputStream dic = new ByteArrayInputStream("1\nfoo\n".getBytes(StandardCharsets.UTF_8))) {
      return new Dictionary(dir, "dictionary", affix, dic);
    }
  }

  private static void assertRejectedWithParseException(String aff) {
    Throwable t = expectThrows(Throwable.class, () -> load(aff));
    assertTrue(
        "expected ParseException for a truncated affix file, got " + t, t instanceof ParseException);
  }

  /** Control: a truncated SFX table is already reported as a ParseException. */
  public void testTruncatedSuffixTableControl() {
    assertRejectedWithParseException("SET UTF-8\nSFX A Y 2\nSFX A 0 s .\n");
  }

  public void testTruncatedRepTable() {
    assertRejectedWithParseException("SET UTF-8\nREP 2\nREP a b\n");
  }

  public void testTruncatedMapTable() {
    assertRejectedWithParseException("SET UTF-8\nMAP 2\nMAP ab\n");
  }

  public void testTruncatedIconvTable() {
    assertRejectedWithParseException("SET UTF-8\nICONV 2\nICONV a b\n");
  }

  public void testTruncatedCompoundRuleTable() {
    assertRejectedWithParseException("SET UTF-8\nCOMPOUNDRULE 2\nCOMPOUNDRULE AB\n");
  }

  public void testTruncatedBreakTable() {
    assertRejectedWithParseException("SET UTF-8\nBREAK 2\nBREAK -\n");
  }

  public void testTruncatedCheckCompoundPatternTable() {
    assertRejectedWithParseException("SET UTF-8\nCHECKCOMPOUNDPATTERN 2\nCHECKCOMPOUNDPATTERN a b\n");
  }
}
