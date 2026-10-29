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
package org.apache.lucene.analysis.ja.completion;

import java.util.List;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.CharsRef;

public class TestKatakanaRomanizerCharsRefOffset extends LuceneTestCase {

  public void testRomanizeSliceAtNonZeroOffset() {
    CharsRef slice = new CharsRef("アトウキョウ".toCharArray(), 1, 5);
    // the slice is "トウキョウ": expected [toukyou]; actual [atoukyo] (chars [0, 5) are read)
    assertEquals(List.of(new CharsRef("toukyou")), KatakanaRomanizer.getInstance().romanize(slice));
  }
}
