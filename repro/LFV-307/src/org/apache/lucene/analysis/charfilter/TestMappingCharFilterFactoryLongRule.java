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
package org.apache.lucene.analysis.charfilter;

import java.io.Reader;
import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.StringMockResourceLoader;

/**
 * MappingCharFilterFactory unescapes every mapping-rule string into a fixed char[256] field
 * (parseString), so a rule whose source or target is longer than 256 characters makes inform()
 * throw ArrayIndexOutOfBoundsException, although NormalizeCharMap accepts strings of any length
 * and the factory documents no limit. Public API only.
 */
public class TestMappingCharFilterFactoryLongRule extends LuceneTestCase {

  private static String map(String rules, String input) throws Exception {
    MappingCharFilterFactory f = new MappingCharFilterFactory(new HashMap<>(Map.of("mapping", "mapping.txt")));
    f.inform(new StringMockResourceLoader(rules));
    try (Reader r = f.create(new StringReader(input))) {
      StringBuilder sb = new StringBuilder();
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
      return sb.toString();
    }
  }

  /** Control: a 256-character target works. */
  public void testTarget256() throws Exception {
    String target = "x".repeat(256);
    assertEquals(target, map("\"abc\" => \"" + target + "\"\n", "abc"));
  }

  public void testTarget257() throws Exception {
    String target = "x".repeat(257);
    assertEquals(target, map("\"abc\" => \"" + target + "\"\n", "abc"));
  }

  public void testSource300() throws Exception {
    String source = "y".repeat(300);
    assertEquals("z", map("\"" + source + "\" => \"z\"\n", source));
  }

  /** The same strings work when the map is built directly with NormalizeCharMap.Builder. */
  public void testBuilderAcceptsLongStrings() throws Exception {
    NormalizeCharMap.Builder b = new NormalizeCharMap.Builder();
    String target = "x".repeat(300);
    b.add("abc", target);
    try (Reader r = new MappingCharFilter(b.build(), new StringReader("abc"))) {
      StringBuilder sb = new StringBuilder();
      int c;
      while ((c = r.read()) != -1) {
        sb.append((char) c);
      }
      assertEquals(target, sb.toString());
    }
  }
}
