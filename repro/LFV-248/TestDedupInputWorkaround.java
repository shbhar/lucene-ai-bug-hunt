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

import java.io.IOException;
import java.io.StringReader;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * Workaround for released versions: drop repeated copies of a suggestion text before build(),
 * keeping its highest weight (what build() itself keeps). No payloads or contexts in this sketch.
 */
public class TestDedupInputWorkaround extends LuceneTestCase {

  static InputIterator dedup(InputIterator in) throws IOException {
    Map<BytesRef, Long> best = new LinkedHashMap<>();
    for (BytesRef text = in.next(); text != null; text = in.next()) {
      best.merge(BytesRef.deepCopyOf(text), in.weight(), Math::max);
    }
    Iterator<Map.Entry<BytesRef, Long>> it = best.entrySet().iterator();
    return new InputIterator() {
      Map.Entry<BytesRef, Long> current;

      @Override
      public BytesRef next() {
        current = it.hasNext() ? it.next() : null;
        return current == null ? null : current.getKey();
      }

      @Override
      public long weight() {
        return current.getValue();
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }

  public void testDedupBeforeBuildKeepsVariant() throws Exception {
    String dict = "Apple iPhone\t100\n".repeat(256) + "apple iphone\t5\n";
    try (Directory tempDir = newDirectory();
        Analyzer a = new StandardAnalyzer()) {
      AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a);
      s.build(dedup(new FileDictionary(new StringReader(dict)).getEntryIterator()));
      assertEquals("[Apple iPhone/100, apple iphone/5]", s.lookup("apple ip", false, 2).toString());
    }
  }
}
