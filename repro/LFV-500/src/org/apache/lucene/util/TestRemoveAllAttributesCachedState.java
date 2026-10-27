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
package org.apache.lucene.util;

import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * AttributeSource.removeAllAttributes() "Removes all attributes and their implementations from
 * this AttributeSource", but it does not reset the cached State chain that captureState(),
 * clearAttributes(), getAttributeImplsIterator(), reflectWith() and hashCode() walk. Any of these
 * calls made before removeAllAttributes() leaves that chain populated with the removed attributes.
 */
public class TestRemoveAllAttributesCachedState extends LuceneTestCase {

  private static AttributeSource sourceAfterRemoveAll() {
    AttributeSource src = new AttributeSource();
    src.addAttribute(CharTermAttribute.class).append("foo");
    src.addAttribute(FlagsAttribute.class).setFlags(5);
    src.clearAttributes(); // what every Tokenizer does per token; builds the cached State chain
    src.addAttribute(CharTermAttribute.class).append("foo");
    src.removeAllAttributes();
    assertFalse(src.hasAttributes());
    return src;
  }

  /** captureState() returns null for a source without attributes (as for a new one). */
  public void testCaptureStateAfterRemoveAll() {
    AttributeSource src = sourceAfterRemoveAll();
    assertNull(new AttributeSource().captureState());
    assertNull("captureState() after removeAllAttributes()", src.captureState());
  }

  /** The state returned by captureState() can be restored into the same source. */
  public void testRestoreOwnStateAfterRemoveAll() {
    AttributeSource src = sourceAfterRemoveAll();
    AttributeSource.State state = src.captureState();
    src.restoreState(state); // throws IllegalArgumentException on main
  }

  /** No attribute implementations are iterated or reflected after removeAllAttributes(). */
  public void testIteratorAndReflectionAfterRemoveAll() {
    AttributeSource src = sourceAfterRemoveAll();
    assertEquals("reflectAsString()", "", src.reflectAsString(false));
    assertFalse("getAttributeImplsIterator().hasNext()", src.getAttributeImplsIterator().hasNext());
  }

  /** equals() and hashCode() agree: an emptied source equals a new one, so the hashes match. */
  public void testEqualsHashCodeAfterRemoveAll() {
    AttributeSource src = sourceAfterRemoveAll();
    AttributeSource empty = new AttributeSource();
    assertEquals(empty, src);
    assertEquals(src, empty);
    assertEquals("hashCode() of equal sources", empty.hashCode(), src.hashCode());
  }
}
