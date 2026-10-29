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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTruncatedRepTable extends LuceneTestCase {

  public void testRepCountHigherThanLinesAtEndOfFile() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        InputStream aff = new ByteArrayInputStream("REP 2\nREP a b\n".getBytes(StandardCharsets.UTF_8));
        InputStream dic = new ByteArrayInputStream("1\nfoo\n".getBytes(StandardCharsets.UTF_8))) {
      // expected ParseException (tolerateRepRuleCountMismatches() is false by default); actual NullPointerException
      expectThrows(ParseException.class, () -> new Dictionary(dir, "dictionary", aff, dic));
    }
  }
}
