/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.util;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * OpenStringBuilder ("A StringBuilder that allows one to access the array") has
 * write(OpenStringBuilder arr) implemented as write(arr.buf, 0, len): it copies this builder's own
 * length worth of chars from the argument instead of arr's length.
 */
public class TestOpenStringBuilderWriteBuilder extends LuceneTestCase {

  public void testWriteOtherBuilderIntoEmptyBuilder() {
    OpenStringBuilder src = new OpenStringBuilder();
    src.write("abc");
    OpenStringBuilder dst = new OpenStringBuilder();
    dst.write(src);
    assertEquals("abc", dst.toString()); // actual: "" (copies dst.len = 0 chars)
  }

  public void testWriteOtherBuilderIntoNonEmptyBuilder() {
    OpenStringBuilder src = new OpenStringBuilder();
    src.write("ab");
    OpenStringBuilder dst = new OpenStringBuilder();
    dst.write("xyz");
    dst.write(src);
    assertEquals("xyzab", dst.toString()); // actual: "xyzab" followed by one NUL char
  }

  public void testWriteSmallBuilderIntoLongerBuilder() {
    OpenStringBuilder src = new OpenStringBuilder(1);
    src.write('u');
    OpenStringBuilder dst = new OpenStringBuilder();
    dst.write("xyz");
    dst.write(src); // actual: ArrayIndexOutOfBoundsException (copies 3 chars from a 1-char array)
    assertEquals("xyzu", dst.toString());
  }
}
