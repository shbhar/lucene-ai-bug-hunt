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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefIterator;

/**
 * HighFrequencyDictionary: "Terms appearing in less than thresh percentage of documents will be
 * excluded." The minimum document count is computed as (int) (thresh * numDocs), which rounds down,
 * so a term that appears in fewer than thresh * numDocs documents is still included.
 */
public class TestHighFrequencyDictionaryThresholdRounding extends LuceneTestCase {

  private static List<String> terms(DirectoryReader reader, float thresh) throws IOException {
    BytesRefIterator it = new HighFrequencyDictionary(reader, "f", thresh).getEntryIterator();
    List<String> terms = new ArrayList<>();
    for (BytesRef t = it.next(); t != null; t = it.next()) {
      terms.add(t.utf8ToString());
    }
    return terms;
  }

  /**
   * Control (passes on main and must keep passing with a fix): a term in exactly 60% of 25
   * documents is kept with thresh 0.6f, although 0.6f * 25 evaluates to 15.000001f in float.
   */
  public void testTermExactlyAtThresholdIsKept() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        for (int i = 0; i < 25; i++) {
          Document doc = new Document();
          doc.add(new TextField("f", i < 15 ? "sixty" : "other", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(List.of("sixty"), terms(reader, 0.6f));
      }
    }
  }

  public void testTermBelowThresholdIsExcluded() throws IOException {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
        for (String text : new String[] {"common rare", "common", "common"}) {
          Document doc = new Document();
          doc.add(new TextField("f", text, Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(3, reader.numDocs());
        // "rare" appears in 1 of 3 documents (33%), below the 50% threshold.
        BytesRefIterator it = new HighFrequencyDictionary(reader, "f", 0.5f).getEntryIterator();
        List<String> terms = new ArrayList<>();
        for (BytesRef t = it.next(); t != null; t = it.next()) {
          terms.add(t.utf8ToString());
        }
        assertEquals("terms appearing in at least 50% of the documents", List.of("common"), terms);
      }
    }
  }
}
