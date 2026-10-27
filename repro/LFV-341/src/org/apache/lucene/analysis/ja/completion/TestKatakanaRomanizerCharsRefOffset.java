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

/**
 * KatakanaRomanizer#romanize(CharsRef) (public, in an exported package) reads input.chars from
 * index 0 instead of input.offset, so a CharsRef that is a slice of a larger array is romanized
 * from the wrong characters. Uses only public API.
 */
public class TestKatakanaRomanizerCharsRefOffset extends LuceneTestCase {

  public void testRomanizeHonorsCharsRefOffset() {
    KatakanaRomanizer romanizer = KatakanaRomanizer.getInstance();
    char[] buffer = "アトウキョウ".toCharArray();
    CharsRef slice = new CharsRef(buffer, 1, 5);
    assertEquals("トウキョウ", slice.toString());

    List<CharsRef> expected = romanizer.romanize(new CharsRef("トウキョウ"));
    assertEquals(List.of(new CharsRef("toukyou")), expected);
    assertEquals("romanize of a CharsRef slice at offset 1", expected, romanizer.romanize(slice));
  }

  /**
   * A slice with a lowercase suffix (the mid-IME shape): reading from index 0 starts at 'x', which
   * matches no keystroke, so no romaji is produced at all.
   */
  public void testSliceWithLowercaseSuffix() {
    KatakanaRomanizer romanizer = KatakanaRomanizer.getInstance();
    char[] buffer = "xトウキョウt".toCharArray();
    CharsRef slice = new CharsRef(buffer, 1, 6);
    assertEquals("トウキョウt", slice.toString());
    assertEquals(
        List.of(new CharsRef("toukyout")), romanizer.romanize(new CharsRef("トウキョウt")));
    assertEquals(List.of(new CharsRef("toukyout")), romanizer.romanize(slice));
  }
}
