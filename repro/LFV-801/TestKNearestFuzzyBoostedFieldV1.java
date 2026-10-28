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
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * KNearestFuzzyClassifier constructor javadoc: "textFieldNames the name of the fields used as the
 * inputs for the classifier, they can contain boosting indication e.g. title^10".
 *
 * <p>A boost on a field cannot change which documents are nearest when that field is the only one
 * that matches the input, so the class assigned with "f^N" must be the class assigned with "f".
 * Uses only public API.
 */
public class TestKNearestFuzzyBoostedFieldV1 extends LuceneTestCase {

  private static final String CLS = "category";
  private static final String TITLE = "title";
  private static final String BODY = "body";

  private Directory dir;
  private IndexReader reader;
  private Analyzer analyzer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, true);
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(analyzer))) {
      add(w, "fruit", "orange lemon", "zzqx wwvy");
      add(w, "fruit", "lemon grape", "wwvy kkjh");
      add(w, "fruit", "orange grape lemon", "kkjh zzqx");
      add(w, "tool", "hammer wrench", "zzqx wwvy");
      add(w, "tool", "wrench drill", "wwvy kkjh");
      add(w, "tool", "hammer drill wrench", "kkjh zzqx");
    }
    reader = DirectoryReader.open(dir);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static void add(IndexWriter w, String cls, String title, String body) throws Exception {
    Document d = new Document();
    d.add(new StringField(CLS, cls, Field.Store.YES));
    d.add(new TextField(TITLE, title, Field.Store.NO));
    d.add(new TextField(BODY, body, Field.Store.NO));
    w.addDocument(d);
  }

  private String fuzzyClass(String text, String... fields) throws Exception {
    KNearestFuzzyClassifier c =
        new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, CLS, fields);
    ClassificationResult<BytesRef> r = c.assignClass(text);
    return r == null ? null : r.assignedClass().utf8ToString();
  }

  /** Single field: "title" vs "title^5". */
  public void testSingleBoostedField() throws Exception {
    String plain = fuzzyClass("lemon orange", TITLE);
    assertEquals("control: unboosted field name", "fruit", plain);
    String boosted = fuzzyClass("lemon orange", TITLE + "^5");
    assertEquals("documented 'title^5' syntax must classify like 'title'", plain, boosted);
  }

  /** getClasses must not be empty for the documented syntax. */
  public void testGetClassesBoostedField() throws Exception {
    KNearestFuzzyClassifier c =
        new KNearestFuzzyClassifier(reader, null, analyzer, null, 3, CLS, TITLE + "^5");
    List<ClassificationResult<BytesRef>> classes = c.getClasses("hammer drill");
    assertFalse("documented 'title^5' syntax: getClasses returned no classes", classes.isEmpty());
    assertEquals("tool", classes.get(0).assignedClass().utf8ToString());
  }

  /**
   * Two fields, only the boosted one matches the input (body terms are > 1 edit away from the
   * input). A boosted field must still contribute.
   */
  public void testMultiFieldOnlyBoostedFieldMatches() throws Exception {
    String plain = fuzzyClass("hammer wrench", TITLE, BODY);
    assertEquals("control: unboosted field names", "tool", plain);
    String boosted = fuzzyClass("hammer wrench", TITLE + "^10", BODY);
    assertEquals("documented 'title^10' syntax must still use the title field", plain, boosted);
  }

  /** Differential control: the sibling classifier with the same javadoc honours the syntax. */
  public void testSiblingKNearestNeighborHonoursBoostSyntax() throws Exception {
    KNearestNeighborClassifier knn =
        new KNearestNeighborClassifier(reader, null, analyzer, null, 3, 1, 1, CLS, TITLE + "^5");
    ClassificationResult<BytesRef> r = knn.assignClass("lemon orange");
    assertNotNull("sibling KNearestNeighborClassifier with 'title^5'", r);
    assertEquals("fruit", r.assignedClass().utf8ToString());
  }
}
