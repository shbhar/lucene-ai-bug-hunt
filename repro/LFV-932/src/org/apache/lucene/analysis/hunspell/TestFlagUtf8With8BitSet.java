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

public class TestFlagUtf8With8BitSet extends LuceneTestCase {

  public void testUtf8FlagsSharingLeadByteStayDistinct() throws Exception {
    String aff =
        "SET ISO8859-2\n"
            + "FLAG UTF-8\n"
            + "SFX á Y 1\n"
            + "SFX á 0 a .\n"
            + "SFX â Y 1\n"
            + "SFX â 0 e .\n";
    String dic = "1\nkot/á\n";
    Dictionary dictionary =
        new Dictionary(
            new ByteBuffersDirectory(),
            "dict",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8)));
    assertFalse(
        "expected kote to be rejected (kot has only flag U+00E1, suffix e needs U+00E2),"
            + " but spell(\"kote\") returned true",
        new Hunspell(dictionary).spell("kote"));
  }
}
