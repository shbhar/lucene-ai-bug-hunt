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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * {@link DatasetSplitter#split} with an explicit list of field names should copy every value of a
 * multi-valued field, as it does when the list is null ("all should be used").
 */
public class TestDatasetSplitterNamedFieldsMultiValued extends LuceneTestCase {

  /** What every output index should hold: its one document keeps both values of "text". */
  private static final List<String> EXPECTED =
      List.of(
          "test: d0 text=[first value, second value] docFreq(text:second)=1",
          "cv: d1 text=[first value, second value] docFreq(text:second)=1",
          "training: d2 text=[first value, second value] docFreq(text:second)=1");

  /**
   * Indexes 3 documents, each with two stored values of "text", splits them with ratios 0.3 / 0.3
   * (one document into each output index) and describes what each output index holds.
   */
  private static List<String> split(String... fieldNames) throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory source = newDirectory();
        Directory training = newDirectory();
        Directory test = newDirectory();
        Directory cv = newDirectory()) {
      try (IndexWriter writer = new IndexWriter(source, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < 3; i++) {
          Document doc = new Document();
          doc.add(new StringField("id", "d" + i, Field.Store.YES));
          doc.add(new TextField("text", "first value", Field.Store.YES));
          doc.add(new TextField("text", "second value", Field.Store.YES));
          doc.add(new StringField("cls", "a", Field.Store.YES));
          doc.add(new SortedDocValuesField("cls", new BytesRef("a")));
          writer.addDocument(doc);
        }
      }
      DirectoryReader reader = DirectoryReader.open(source);
      new DatasetSplitter(0.3, 0.3)
          .split(reader, training, test, cv, analyzer, false, "cls", fieldNames);
      reader.close(); // split() closes it already; a second close is a no-op

      List<String> out = new ArrayList<>();
      String[] names = {"test", "cv", "training"};
      Directory[] dirs = {test, cv, training};
      for (int k = 0; k < 3; k++) {
        try (DirectoryReader r = DirectoryReader.open(dirs[k])) {
          assertEquals(names[k] + " index size", 1, r.maxDoc());
          Document copy = r.storedFields().document(0);
          out.add(
              names[k]
                  + ": "
                  + copy.get("id")
                  + " text="
                  + Arrays.toString(copy.getValues("text"))
                  + " docFreq(text:second)="
                  + r.docFreq(new Term("text", "second")));
        }
      }
      return out;
    }
  }

  /** Control: no field names, every field is copied with all of its values. Passes. */
  public void testAllFieldsKeepsEveryValue() throws Exception {
    assertEquals(EXPECTED, split());
  }

  /** Naming every stored field should give the same output as the control. Fails. */
  public void testNamedFieldsKeepEveryValue() throws Exception {
    assertEquals(EXPECTED, split("id", "text", "cls"));
  }
}
