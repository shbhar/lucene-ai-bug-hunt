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
package org.apache.lucene.search.suggest;

import java.io.ByteArrayInputStream;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestFileDictionaryUtf8Bom extends LuceneTestCase {

  public void testFirstEntryAfterByteOrderMark() throws Exception {
    byte[] file = {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF, 'f', 'o', 'o', '\t', '5', '\n'};
    InputIterator it = new FileDictionary(new ByteArrayInputStream(file)).getEntryIterator();
    // expected: "foo" ([66 6f 6f]); actual: the UTF-8 mark is kept as U+FEFF ([ef bb bf 66 6f 6f])
    assertEquals(new BytesRef("foo"), it.next());
  }
}
