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
import java.nio.charset.UnsupportedCharsetException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSetEncodingIso8859_10 extends LuceneTestCase {

  public void testSetIso8859_10Loads() throws Exception {
    byte[] aff = "SET ISO8859-10\n".getBytes(StandardCharsets.US_ASCII);
    byte[] dic = {'1', '\n', 'x', (byte) 0xBF, 'y', '\n'};
    Dictionary dictionary;
    try {
      dictionary =
          new Dictionary(
              new ByteBuffersDirectory(),
              "d",
              new ByteArrayInputStream(aff),
              new ByteArrayInputStream(dic));
    } catch (UnsupportedCharsetException e) {
      // expected: SET ISO8859-10, a value hunspell(5) lists, loads; actual: the constructor throws
      throw new AssertionError("expected SET ISO8859-10 to load, got " + e, e);
    }
    assertTrue(
        "ISO-8859-10 byte 0xBF should read as U+014B", new Hunspell(dictionary).spell("x\u014By"));
  }
}
