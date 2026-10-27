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
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * V3 check: DatasetSplitter.split with explicit fieldNames vs fieldNames == null, on documents with
 * a multi-valued stored text field ("tags"). Public API only. All documents go to the training
 * index (ratios 0/0), so the three output indexes do not matter here.
 */
public class TestDatasetSplitterNamedFieldsV3 extends LuceneTestCase {

  private static final int NUM_DOCS = 6;

  /** Returns {stored tag values of doc 0, hits of tags:beta, hits of tags:gamma} in the training index. */
  private static Object[] split(String... fieldNames) throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    try (Directory src = newDirectory();
        Directory train = newDirectory();
        Directory test = newDirectory();
        Directory cv = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(analyzer))) {
        for (int i = 0; i < NUM_DOCS; i++) {
          Document d = new Document();
          d.add(new StringField("id", "doc" + i, Field.Store.YES));
          d.add(new TextField("tags", "alpha", Field.Store.YES));
          d.add(new TextField("tags", "beta", Field.Store.YES));
          d.add(new TextField("tags", "gamma", Field.Store.YES));
          String cls = (i % 2 == 0) ? "even" : "odd";
          d.add(new StringField("cls", cls, Field.Store.YES));
          d.add(new SortedDocValuesField("cls", new BytesRef(cls)));
          w.addDocument(d);
        }
      }
      DirectoryReader reader = DirectoryReader.open(src); // split() closes it
      new DatasetSplitter(0, 0).split(reader, train, test, cv, analyzer, false, "cls", fieldNames);
      try (DirectoryReader r = DirectoryReader.open(train)) {
        assertEquals(NUM_DOCS, r.numDocs());
        IndexSearcher s = new IndexSearcher(r);
        String[] tags = r.storedFields().document(0).getValues("tags");
        int beta = s.count(new TermQuery(new Term("tags", "beta")));
        int gamma = s.count(new TermQuery(new Term("tags", "gamma")));
        return new Object[] {Arrays.asList(tags), beta, gamma};
      }
    }
  }

  /** Control: fieldNames == null copies every value of the multi-valued field. Expected to pass. */
  public void testAllFieldsModeKeepsEveryValue() throws Exception {
    Object[] res = split();
    assertEquals(Arrays.asList("alpha", "beta", "gamma"), res[0]);
    assertEquals(NUM_DOCS, res[1]);
    assertEquals(NUM_DOCS, res[2]);
  }

  /** Naming every field the source document has should give the same output as the null mode. */
  public void testNamedModeKeepsEveryStoredValue() throws Exception {
    Object[] res = split("id", "tags", "cls");
    assertEquals(
        "named mode lost stored values of 'tags'", Arrays.asList("alpha", "beta", "gamma"), res[0]);
  }

  /** The lost values are also missing from the inverted index, so classifiers never see them. */
  public void testNamedModeKeepsEveryIndexedValue() throws Exception {
    Object[] res = split("id", "tags", "cls");
    assertEquals("tags:beta hits in training index (named mode)", NUM_DOCS, res[1]);
    assertEquals("tags:gamma hits in training index (named mode)", NUM_DOCS, res[2]);
  }
}
