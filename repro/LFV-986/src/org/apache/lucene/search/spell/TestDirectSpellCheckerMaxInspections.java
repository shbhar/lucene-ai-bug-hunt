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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDirectSpellCheckerMaxInspections extends LuceneTestCase {

  public void testMaxInspectionsMaxValue() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("f", "lucene", Field.Store.NO));
      writer.addDocument(doc);
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        DirectSpellChecker checker = new DirectSpellChecker();
        checker.setMaxInspections(Integer.MAX_VALUE);
        SuggestWord[] words = checker.suggestSimilar(new Term("f", "luxeny"), 2, reader);
        // expected [lucene] (the default maxInspections finds it); actual [] (2 * MAX_VALUE wraps)
        assertEquals(List.of("lucene"), Arrays.stream(words).map(w -> w.string).toList());
      }
    }
  }
}
