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
package org.apache.lucene.search.spell;

import java.util.List;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectSpellCheckerLevenshteinDistance extends LuceneTestCase {

  public void testBestCandidateIsInspected() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(List.of(new StringField("f", "abc", Field.Store.NO)));
        w.addDocument(List.of(new StringField("f", "abcde", Field.Store.NO)));
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        DirectSpellChecker sc = new DirectSpellChecker();
        sc.setDistance(new LevenshteinDistance());
        sc.setMaxInspections(1);
        SuggestWord[] s =
            sc.suggestSimilar(new Term("f", "abcd"), 1, r, SuggestMode.SUGGEST_ALWAYS);
        // expected: abcde (closer to abcd than abc under both similarities); actual: abc
        assertEquals("abcde", s[0].string);
      }
    }
  }
}
