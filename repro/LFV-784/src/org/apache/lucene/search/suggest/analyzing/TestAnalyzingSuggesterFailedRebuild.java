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
package org.apache.lucene.search.suggest.analyzing;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestAnalyzingSuggesterFailedRebuild extends LuceneTestCase {

  public void testFailedRebuildWithoutPayloadsKeepsPayload() throws Exception {
    AnalyzingSuggester suggester =
        new AnalyzingSuggester(new ByteBuffersDirectory(), "suggest", new StandardAnalyzer());
    byte[] dictionary = "foo\t10\tp1\n".getBytes(StandardCharsets.UTF_8);
    suggester.build(new FileDictionary(new ByteArrayInputStream(dictionary)).getEntryIterator());
    InputIterator failing =
        new InputIterator.InputIteratorWrapper(
            () -> {
              throw new IOException("read failure");
            });
    expectThrows(IOException.class, () -> suggester.build(failing));
    LookupResult r = suggester.lookup("fo", false, 1).get(0);
    String key = r.key.toString().replace("\u001F", "<U+001F>");
    String payload = r.payload == null ? null : r.payload.utf8ToString();
    // expected: foo with payload p1 (the previous build); actual: key foo<U+001F>p1, payload null
    assertEquals("foo payload=p1", key + " payload=" + payload);
  }
}
