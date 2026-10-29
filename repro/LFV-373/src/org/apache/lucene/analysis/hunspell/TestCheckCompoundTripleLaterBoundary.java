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

public class TestCheckCompoundTripleLaterBoundary extends LuceneTestCase {

  public void testTripleLetterAtSecondBoundary() throws Exception {
    byte[] aff = "CHECKCOMPOUNDTRIPLE\nCOMPOUNDFLAG A\n".getBytes(StandardCharsets.UTF_8);
    byte[] dic = "3\nopera/A\nbare/A\neel/A\n".getBytes(StandardCharsets.UTF_8);
    Dictionary dictionary =
        new Dictionary(
            new ByteArrayInputStream(aff),
            List.of(new ByteArrayInputStream(dic)),
            false,
            SortingStrategy.inMemory());
    // expected: rejected, as bareeel is (bare|eel has "eee"); actual: accepted
    assertEquals("spell(\"operabareeel\")", false, new Hunspell(dictionary).spell("operabareeel"));
  }
}
