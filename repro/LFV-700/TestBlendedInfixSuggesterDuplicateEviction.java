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

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.suggest.DocumentDictionary;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A default BlendedInfixSuggester (POSITION_LINEAR, numFactor 10) built from a DocumentDictionary
 * whose suggestion field repeats one value with the same weight. Identical suggestions are
 * collapsed into one (as intended, see testBlendedInfixSuggesterDedupsOnWeightTitleAndPayload), but
 * when the bounded result set is already full the collapse also throws away another, distinct
 * suggestion.
 */
public class TestBlendedInfixSuggesterDuplicateEviction extends LuceneTestCase {

  /** Builds the suggester from documents (weights == null: no weight field) and looks up key. */
  private static String suggest(String[] names, long[] weights, String key, int num)
      throws Exception {
    Analyzer a = new StandardAnalyzer(CharArraySet.EMPTY_SET);
    try (Directory src = newDirectory();
        Directory sugDir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(src, new IndexWriterConfig(a))) {
        for (int i = 0; i < names.length; i++) {
          Document d = new Document();
          d.add(new TextField("name", names[i], Field.Store.YES));
          if (weights != null) {
            d.add(new StoredField("weight", weights[i]));
          }
          w.addDocument(d);
        }
      }
      try (DirectoryReader r = DirectoryReader.open(src);
          BlendedInfixSuggester s = new BlendedInfixSuggester(sugDir, a)) {
        s.build(new DocumentDictionary(r, "name", weights == null ? null : "weight"));
        return s.lookup(key, false, num).toString();
      }
    } finally {
      a.close();
    }
  }

  /** The weaker "my lake" is returned and the stronger "the lake" is lost. */
  public void testLowerRankedSuggestionReplacesHigherRankedOne() throws Exception {
    String[] names = {"the lake", "lake", "lake", "my lake"};
    long[] weights = {1000, 950, 950, 920};
    // blended (weight * (1 - 0.1 * position)): the lake 900, lake 950, lake 950, my lake 828
    assertEquals(
        "[lake/950, the lake/900, my lake/828]", suggest(names, weights, "lake", 3)); // passes
    assertEquals("[lake/950, the lake/900]", suggest(names, weights, "lake", 2)); // fails
  }

  /** No weight field: DocumentDictionary documents the weight as 0 for every entry. */
  public void testDuplicateNamesWithoutWeightField() throws Exception {
    String[] names = {"the apple", "the big apple", "the apple", "a big red apple"};
    assertEquals(
        "[the apple/9, the big apple/8, a big red apple/7]",
        suggest(names, null, "apple", 5)); // passes
    assertEquals("[the apple/9, the big apple/8]", suggest(names, null, "apple", 2)); // fails
  }

  /** Two distinct suggestions match, num=2, and only one comes back. */
  public void testOneSuggestionShort() throws Exception {
    String[] names = {"the lake", "lake", "lake"};
    long[] weights = {1000, 950, 950};
    assertEquals("[lake/950, the lake/900]", suggest(names, weights, "lake", 2)); // fails
  }

  /** Control: the same entries without the repeated "lake". */
  public void testControlWithoutDuplicate() throws Exception {
    String[] names = {"the lake", "lake", "my lake"};
    long[] weights = {1000, 950, 920};
    assertEquals("[lake/950, the lake/900]", suggest(names, weights, "lake", 2)); // passes
  }
}
