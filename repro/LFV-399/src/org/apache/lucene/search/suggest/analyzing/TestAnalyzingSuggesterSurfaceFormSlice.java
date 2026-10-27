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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup.LookupResult;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * AnalyzingSuggester (with payloads) rejects surface forms that contain the reserved byte 0x1F, but
 * the check reads surfaceForm.bytes[0, length) instead of [offset, offset + length), so a surface
 * form handed over as a BytesRef slice is validated against the wrong bytes.
 */
public class TestAnalyzingSuggesterSurfaceFormSlice extends LuceneTestCase {

  /** Emits one (surface, payload, weight) entry; surface and payload are given as BytesRef. */
  private static final class OneEntryIterator implements InputIterator {
    private final BytesRef surface;
    private final BytesRef payload;
    private boolean done;

    OneEntryIterator(BytesRef surface, BytesRef payload) {
      this.surface = surface;
      this.payload = payload;
    }

    @Override
    public BytesRef next() {
      if (done) {
        return null;
      }
      done = true;
      return surface;
    }

    @Override
    public long weight() {
      return 7;
    }

    @Override
    public BytesRef payload() {
      return payload;
    }

    @Override
    public boolean hasPayloads() {
      return true;
    }

    @Override
    public Set<BytesRef> contexts() {
      return null;
    }

    @Override
    public boolean hasContexts() {
      return false;
    }
  }

  private static BytesRef utf8(String s) {
    return new BytesRef(s.getBytes(StandardCharsets.UTF_8));
  }

  private static String describe(List<LookupResult> results) {
    StringBuilder sb = new StringBuilder();
    for (LookupResult r : results) {
      sb.append("[key=")
          .append(r.key.toString().replace("\u001F", "<0x1F>"))
          .append(" payload=")
          .append(r.payload.utf8ToString().replace("\u001F", "<0x1F>"))
          .append(']');
    }
    return sb.toString();
  }

  /** "ab" stored at offset 1 of {0x1F, 'a', 'b'}: the surface form itself has no 0x1F. */
  public void testSliceWithoutReservedByteIsAccepted() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    byte[] backing = {0x1F, 'a', 'b'};
    BytesRef slice = new BytesRef(backing, 1, 2);
    assertEquals("ab", slice.utf8ToString());

    try (Directory compactDir = newDirectory();
        Directory sliceDir = newDirectory()) {
      // control: the same bytes as a compact BytesRef are accepted
      AnalyzingSuggester compact = new AnalyzingSuggester(compactDir, "suggest", a);
      compact.build(new OneEntryIterator(BytesRef.deepCopyOf(slice), utf8("p")));
      assertEquals("[key=ab payload=p]", describe(compact.lookup("a", false, 5)));

      AnalyzingSuggester sliced = new AnalyzingSuggester(sliceDir, "suggest", a);
      try {
        sliced.build(new OneEntryIterator(slice, utf8("p")));
      } catch (IllegalArgumentException e) {
        throw new AssertionError(
            "surface form \"ab\" (a BytesRef slice at offset 1) was rejected although it does not"
                + " contain 0x1F; its compact copy is accepted: "
                + e.getMessage(),
            e);
      }
      assertEquals("[key=ab payload=p]", describe(sliced.lookup("a", false, 5)));
    }
    a.close();
  }

  /** "a<0x1F>b" stored at offset 3 of {'x','x','x','a',0x1F,'b'}: must be rejected like its copy. */
  public void testReservedByteInsideSliceIsRejected() throws Exception {
    Analyzer a = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    byte[] backing = {'x', 'x', 'x', 'a', 0x1F, 'b'};
    BytesRef slice = new BytesRef(backing, 3, 3);
    assertEquals("a\u001Fb", slice.utf8ToString());

    try (Directory compactDir = newDirectory();
        Directory sliceDir = newDirectory()) {
      // control: the compact copy is rejected with the documented message
      AnalyzingSuggester compact = new AnalyzingSuggester(compactDir, "suggest", a);
      IllegalArgumentException expected =
          expectThrows(
              IllegalArgumentException.class,
              () -> compact.build(new OneEntryIterator(BytesRef.deepCopyOf(slice), utf8("p"))));
      assertTrue(expected.getMessage(), expected.getMessage().contains("U+001F"));

      AnalyzingSuggester sliced = new AnalyzingSuggester(sliceDir, "suggest", a);
      boolean rejected = false;
      try {
        sliced.build(new OneEntryIterator(slice, utf8("p")));
      } catch (IllegalArgumentException e) {
        rejected = true;
      }
      if (rejected == false) {
        String after;
        try {
          after = "lookup(\"a\") returns " + describe(sliced.lookup("a", false, 5));
        } catch (Throwable t) {
          after = "lookup(\"a\") throws " + t;
        }
        fail(
            "surface form \"a<0x1F>b\" (a BytesRef slice at offset 3) was accepted although its"
                + " compact copy is rejected; "
                + after);
      }
    }
    a.close();
  }
}
