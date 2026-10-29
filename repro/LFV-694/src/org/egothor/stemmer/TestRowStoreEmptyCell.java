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
package org.egothor.stemmer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRowStoreEmptyCell extends LuceneTestCase {

  public void testRowWithEmptyCellRoundTrip() throws Exception {
    Row first = new Row();
    first.setCmd('a', 0);
    first.setCmd('b', 1);
    first.setCmd('b', -1);
    Row second = new Row();
    second.setCmd('x', 3);

    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(bytes);
    first.store(out);
    second.store(out);

    DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
    new Row(in);
    // Expected 3, actual -1: store() counts the emptied cell 'b' (as Gener leaves it) but skips it.
    assertEquals("command of 'x' in the second stored row", 3, new Row(in).getCmd('x'));
  }
}
