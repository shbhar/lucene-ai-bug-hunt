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

import java.io.IOException;
import java.io.StringReader;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * FileDictionary documents its line format as "suggestion fieldDelimiter weight fieldDelimiter
 * payload" with a caller-chosen fieldDelimiter string. The delimiter is passed to String.split,
 * which interprets it as a regular expression, so delimiters that are regex metacharacters do not
 * split on the delimiter text.
 */
public class TestFileDictionaryDelimiterIsRegex extends LuceneTestCase {

  private static void assertEntry(InputIterator it, String term, long weight, String payload)
      throws IOException {
    assertEquals(new BytesRef(term), it.next());
    assertEquals(weight, it.weight());
    assertEquals(new BytesRef(payload), it.payload());
  }

  /** "|" as the delimiter: building the iterator fails although the file follows the format. */
  public void testPipeDelimiter() throws IOException {
    String file = "foo bar|10|p1\nbaz|20|p2\n";
    FileDictionary dictionary = new FileDictionary(new StringReader(file), "|");
    InputIterator it = dictionary.getEntryIterator();
    assertTrue(it.hasPayloads());
    assertEntry(it, "foo bar", 10, "p1");
    assertEntry(it, "baz", 20, "p2");
    assertNull(it.next());
  }

  /**
   * "$" as the delimiter: no error at all, but every line becomes one suggestion with weight 1 and
   * no payload; the weight and payload text end up inside the suggestion.
   */
  public void testDollarDelimiterSilentlyMisparses() throws IOException {
    String file = "foo bar$10$p1\nbaz$20$p2\n";
    FileDictionary dictionary = new FileDictionary(new StringReader(file), "$");
    InputIterator it = dictionary.getEntryIterator();
    BytesRef first = it.next();
    assertEquals("suggestion of line 1", "foo bar", first.utf8ToString());
    assertEquals("weight of line 1", 10, it.weight());
    assertTrue("payloads enabled by the first line", it.hasPayloads());
  }

  /** The same file built into a suggester: the suggestion text contains the weight and payload. */
  public void testDollarDelimiterThroughSuggester() throws IOException {
    String file = "foo bar$10$p1\nbaz$20$p2\n";
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    try (Directory tempDir = newDirectory()) {
      AnalyzingSuggester suggester = new AnalyzingSuggester(tempDir, "suggest", a);
      suggester.build(new FileDictionary(new StringReader(file), "$"));
      List<Lookup.LookupResult> results = suggester.lookup("foo", false, 5);
      assertEquals(1, results.size());
      assertEquals("foo bar", results.get(0).key.toString());
      assertEquals(10, results.get(0).value);
      assertEquals(new BytesRef("p1"), results.get(0).payload);
    } finally {
      a.close();
    }
  }

  /** "." as the delimiter: ArrayIndexOutOfBoundsException while reading the first line. */
  public void testDotDelimiter() throws IOException {
    String file = "foo.10.p1\n";
    FileDictionary dictionary = new FileDictionary(new StringReader(file), ".");
    InputIterator it = dictionary.getEntryIterator();
    assertEntry(it, "foo", 10, "p1");
    assertNull(it.next());
  }
}
