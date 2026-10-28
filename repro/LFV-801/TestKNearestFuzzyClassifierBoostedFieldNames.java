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
package org.apache.lucene.classification;

import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * The {@link KNearestFuzzyClassifier} constructor documents that textFieldNames "can contain
 * boosting indication e.g. title^10". A boosted entry must still read its field.
 */
public class TestKNearestFuzzyClassifierBoostedFieldNames extends LuceneTestCase {

  /** One text field. */
  private static final String[][] ONE_FIELD = {
    {"fruit", "apple banana cherry"},
    {"fruit", "apple pear"},
    {"vehicle", "car truck bus"},
    {"vehicle", "bus train"}
  };

  /** The title words point to fruit, the body words to vehicle. */
  private static final String[][] TWO_FIELDS = {
    {"fruit", "apple banana", "sweet juice"},
    {"fruit", "apple cherry", "sweet tree"},
    {"fruit", "banana cherry", "juice garden"},
    {"vehicle", "car truck", "wheel engine"},
    {"vehicle", "truck bus", "engine wheel road"},
    {"vehicle", "bus car", "road wheel"}
  };

  private Analyzer analyzer;
  private Directory dir1, dir2;
  private IndexReader oneField, twoFields;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    dir1 = newDirectory();
    oneField = index(dir1, ONE_FIELD, "text");
    dir2 = newDirectory();
    twoFields = index(dir2, TWO_FIELDS, "title", "body");
  }

  @Override
  public void tearDown() throws Exception {
    oneField.close();
    twoFields.close();
    dir1.close();
    dir2.close();
    analyzer.close();
    super.tearDown();
  }

  private IndexReader index(Directory dir, String[][] docs, String... fields) throws Exception {
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : docs) {
        Document doc = new Document();
        doc.add(new StringField("cls", d[0], Field.Store.YES));
        for (int i = 0; i < fields.length; i++) {
          doc.add(new TextField(fields[i], d[i + 1], Field.Store.NO));
        }
        w.addDocument(doc);
      }
    }
    return DirectoryReader.open(dir);
  }

  private String assignedClass(IndexReader reader, String text, String... textFieldNames)
      throws Exception {
    ClassificationResult<BytesRef> result =
        new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, "cls", textFieldNames)
            .assignClass(text);
    return result == null ? null : result.assignedClass().utf8ToString();
  }

  /** With one field, a boost cannot change which documents are nearest. */
  public void testSingleBoostedField() throws Exception {
    assertEquals("fruit", assignedClass(oneField, "apple banana", "text"));
    assertEquals("fruit", assignedClass(oneField, "apple banana", "text^2"));
  }

  public void testGetClassesWithBoostedField() throws Exception {
    assertEquals("vehicle", assignedClass(oneField, "bus train", "text"));
    List<ClassificationResult<BytesRef>> classes =
        new KNearestFuzzyClassifier(oneField, null, analyzer, null, 3, "cls", "text^2")
            .getClasses("bus train");
    assertFalse("getClasses returned no classes for text^2", classes.isEmpty());
    assertEquals("vehicle", classes.get(0).assignedClass().utf8ToString());
  }

  /**
   * "apple" is a fruit title word, "wheel" a vehicle body word: weighting the title keeps fruit.
   */
  public void testBoostedFieldIsNotDropped() throws Exception {
    assertEquals("fruit", assignedClass(twoFields, "apple wheel", "title", "body"));
    assertEquals("vehicle", assignedClass(twoFields, "apple wheel", "body"));
    assertEquals("fruit", assignedClass(twoFields, "apple wheel", "title^10", "body"));
  }

  /** A boost of 1 is no boost. */
  public void testNeutralBoost() throws Exception {
    assertEquals(
        assignedClass(twoFields, "apple wheel", "title", "body"),
        assignedClass(twoFields, "apple wheel", "title^1", "body"));
  }
}
