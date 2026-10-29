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
import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestCompoundPatternReplacementRecursion extends LuceneTestCase {

  public void testReplacementAfterFirstPart() throws Exception {
    String aff = "COMPOUNDFLAG x\nCOMPOUNDMIN 1\nCHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN u u u\n";
    String dic = "1\nau/x\n";
    Dictionary dictionary =
        new Dictionary(
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            List.of(new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))),
            false,
            SortingStrategy.inMemory());
    // expected: false ("auu" is no compound of "au"); actual: StackOverflowError
    assertFalse(new Hunspell(dictionary).spell("auu"));
  }
}
