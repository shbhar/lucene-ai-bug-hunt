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
package org.apache.lucene.index.memory;

import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestMemoryIndexSelectiveIndexing extends LuceneTestCase {

  public void testExactQueryOnOneOfTwoDimensions() {
    FieldType type = new FieldType();
    type.setDimensions(2, 1, Integer.BYTES);
    MemoryIndex index = new MemoryIndex();
    index.addField(new Field("f", IntPoint.pack(10, 5), type), null);
    try {
      assertTrue(index.search(IntPoint.newExactQuery("f", 10)) > 0);
    } catch (IllegalArgumentException e) {
      // expected: a match on the 1 indexed dimension; actual: getNumIndexDimensions() returns 2
      throw new AssertionError("expected f:10 to match, but got " + e, e);
    }
  }
}
