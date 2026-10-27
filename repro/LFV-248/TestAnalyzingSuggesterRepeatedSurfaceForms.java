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

import java.io.StringReader;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.search.suggest.FileDictionary;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Repeated copies of one suggestion text are skipped by build() as duplicates, but each copy still
 * uses up one of the maxSurfaceFormsPerAnalyzedForm slots, so a distinct, lower-weighted surface
 * form with the same analyzed form is dropped.
 */
public class TestAnalyzingSuggesterRepeatedSurfaceForms extends LuceneTestCase {

  /** Limit 2: "a" twice plus "A" once are 2 distinct surface forms, so both should be kept. */
  public void testLimitTwo() throws Exception {
    try (Directory tempDir = newDirectory();
        Analyzer a = new StandardAnalyzer()) {
      AnalyzingSuggester s = new AnalyzingSuggester(tempDir, "suggest", a, a, 0, 2, -1, true);
      s.build(new FileDictionary(new StringReader("a\t10\na\t5\nA\t3\n")));
      assertEquals("[a/10, A/3]", s.lookup("a", false, 5).toString());
    }
  }

  /** Default constructor (limit 256): 256 copies of one text hide its lower-weighted variant. */
  public void testDefaultLimit() throws Exception {
    assertEquals("[Apple iPhone/355, apple iphone/5]", suggest(false, 256, "apple ip"));
  }

  /** FuzzySuggester inherits build(): same loss with its default constructor. */
  public void testFuzzySuggester() throws Exception {
    assertEquals("[Apple iPhone/355, apple iphone/5]", suggest(true, 256, "apple ip"));
  }

  /** EXACT_FIRST (on by default): typing the hidden text exactly does not bring it back. */
  public void testExactMatchIsLostToo() throws Exception {
    assertEquals("[apple iphone/5, Apple iPhone/355]", suggest(false, 256, "apple iphone"));
  }

  /** Control: with 255 copies both surface forms come back (passes today). */
  public void testControl255Copies() throws Exception {
    assertEquals("[Apple iPhone/354, apple iphone/5]", suggest(false, 255, "apple ip"));
  }

  /**
   * Indexes {@code copies} documents "Apple iPhone" (weights 100, 101, ...) and one document "apple
   * iphone" (weight 5), builds a default suggester from a DocumentDictionary and returns the top 2
   * suggestions for key.
   */
  private String suggest(boolean fuzzy, int copies, String key) throws Exception {
    try (Directory indexDir = newDirectory();
        Directory tempDir = newDirectory();
        Analyzer a = new StandardAnalyzer()) {
      try (IndexWriter w = new IndexWriter(indexDir, new IndexWriterConfig(a))) {
        for (int i = 0; i <= copies; i++) {
          Document doc = new Document();
          doc.add(new StoredField("suggest", i < copies ? "Apple iPhone" : "apple iphone"));
          doc.add(new NumericDocValuesField("weight", i < copies ? 100 + i : 5));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(indexDir)) {
        AnalyzingSuggester s =
            fuzzy
                ? new FuzzySuggester(tempDir, "suggest", a)
                : new AnalyzingSuggester(tempDir, "suggest", a);
        s.build(new DocumentDictionary(reader, "suggest", "weight"));
        return s.lookup(key, false, 2).toString();
      }
    }
  }
}
