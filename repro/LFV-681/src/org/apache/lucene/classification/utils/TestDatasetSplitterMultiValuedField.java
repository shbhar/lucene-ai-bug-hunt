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
package org.apache.lucene.classification.utils;

import java.util.Arrays;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * DatasetSplitter.split with explicit fieldNames copies only the first value of each named
 * field (document.getField), while split without fieldNames copies every value.
 */
public class TestDatasetSplitterMultiValuedField extends LuceneTestCase {

  private static String[] splitAndGetTextValues(Analyzer analyzer, String... fieldNames)
      throws Exception {
    try (Directory src = newDirectory();
        Directory train = new ByteBuffersDirectory();
        Directory test = new ByteBuffersDirectory();
        Directory cv = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(analyzer))) {
        Document d = new Document();
        d.add(new StringField("id", "d0", Field.Store.YES));
        d.add(new TextField("text", "first value", Field.Store.YES));
        d.add(new TextField("text", "second value", Field.Store.YES));
        d.add(new StringField("cls", "a", Field.Store.YES));
        d.add(new SortedDocValuesField("cls", new BytesRef("a")));
        w.addDocument(d);
      }
      DirectoryReader reader = DirectoryReader.open(src);
      // ratios 0/0: the single document goes to the training index
      new DatasetSplitter(0, 0).split(reader, train, test, cv, analyzer, false, "cls", fieldNames);
      reader.close(); // split closes it already; closing twice is a no-op
      try (DirectoryReader r = DirectoryReader.open(train)) {
        assertEquals(1, r.maxDoc());
        return r.storedFields().document(0).getValues("text");
      }
    }
  }

  public void testNamedFieldKeepsAllValues() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    String[] all = splitAndGetTextValues(analyzer);
    // control: without fieldNames both values are copied
    assertEquals(Arrays.asList("first value", "second value"), Arrays.asList(all));
    String[] named = splitAndGetTextValues(analyzer, "id", "text", "cls");
    assertEquals(
        "fieldNames {id, text, cls} lost values of the multi-valued field 'text'",
        Arrays.asList("first value", "second value"),
        Arrays.asList(named));
  }
}
