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

import java.io.IOException;
import java.util.List;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.MultiTerms;
import org.apache.lucene.index.TermsEnum;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestMultiTermsNextAfterSeekExact extends LuceneTestCase {

  public void testNextAfterSeekExact() throws IOException {
    List<String> terms = List.of("a", "b", "c", "d", "e");
    MemoryIndex first = new MemoryIndex();
    first.addField("f", first.keywordTokenStream(terms));
    MemoryIndex second = new MemoryIndex();
    second.addField("f", second.keywordTokenStream(terms));
    try (MultiReader reader =
        new MultiReader(
            first.createSearcher().getIndexReader(), second.createSearcher().getIndexReader())) {
      TermsEnum termsEnum = MultiTerms.getTerms(reader, "f").iterator();
      assertTrue(termsEnum.seekExact(new BytesRef("a")));
      // expected: next() returns "b", the term after "a"; actual: "c" ("b" is skipped)
      assertEquals("b", termsEnum.next().utf8ToString());
    }
  }
}
