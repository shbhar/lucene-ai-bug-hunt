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
package org.apache.lucene.search.suggest.document;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Each test writes one segment of suggestions abc_0..abc_N-1 with weights top, top - 1, ... and
 * deletes a few of its documents (the updateDocument test adds a second segment). NRTSuggester
 * enlarges its TopNSearcher queue to (long) (k / liveDocsRatio), where k = topN *
 * maxAnalyzedPathsPerOutput (here k = num), to make room for completions of deleted documents; with
 * few deletions that rounds back down to k, so a deleted completion in a result slot is dropped and
 * nothing replaces it.
 */
public class TestSuggestFewDeletions extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  private Analyzer analyzer;
  private Directory dir;
  private IndexWriter writer;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE); // keep the segment and its deletions
    Codec codec = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(codec.getName(), codec) {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return FIELD.equals(field)
                    ? completion
                    : ((PerFieldPostingsFormat) delegate.postingsFormat())
                        .getPostingsFormatForField(field);
              }
            };
          }
        });
    writer = new IndexWriter(dir, iwc);
  }

  @Override
  public void tearDown() throws Exception {
    writer.close();
    dir.close();
    analyzer.close();
    super.tearDown();
  }

  private static Document doc(int id, String key, int weight) {
    Document doc = new Document();
    doc.add(new StringField("id", Integer.toString(id), Field.Store.NO));
    doc.add(new SuggestField(FIELD, key, weight));
    return doc;
  }

  /** Writes one segment abc_0..abc_{n-1}, weights top, top - 1, ..., then deletes the given ids. */
  private void indexOneSegment(int n, int top, int... deleteIds) throws Exception {
    for (int i = 0; i < n; i++) {
      writer.addDocument(doc(i, "abc_" + i, top - i));
    }
    writer.commit();
    for (int id : deleteIds) {
      writer.deleteDocuments(new Term("id", Integer.toString(id)));
    }
  }

  /** suggest("abc_", num) as key/weight strings, e.g. [abc_1/99]. */
  private List<String> suggest(int num) throws Exception {
    try (DirectoryReader reader = DirectoryReader.open(writer)) {
      SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
      PrefixCompletionQuery query = new PrefixCompletionQuery(analyzer, new Term(FIELD, "abc_"));
      TopSuggestDocs top = searcher.suggest(query, num, false);
      List<String> hits = new ArrayList<>();
      for (TopSuggestDocs.SuggestScoreDoc hit : top.scoreLookupDocs()) {
        hits.add(hit.key + "/" + (long) hit.score);
      }
      return hits;
    }
  }

  /** 1 of 10 deleted (the best): queue (long) (1 / 0.9) = 1, no room for the rejected abc_0. */
  public void testBestOfTenDeleted() throws Exception {
    indexOneSegment(10, 100, 0);
    assertEquals(List.of("abc_1/99"), suggest(1)); // actual: []
  }

  /** 1 of 1,000 deleted: a large segment has no spare slot either, (long) (1 / 0.999) = 1. */
  public void testBestOfThousandDeleted() throws Exception {
    indexOneSegment(1000, 10000, 0);
    assertEquals(List.of("abc_1/9999"), suggest(1)); // actual: []
  }

  /**
   * The usual way to change a suggestion's weight: updateDocument deletes the old copy (weight 100,
   * still the best entry of the first segment) and adds the new one (weight 1) in a second segment.
   * The first segment returns nothing, so the re-weighted entry wins.
   */
  public void testUpdateLowersWeightOfBest() throws Exception {
    indexOneSegment(10, 100);
    writer.updateDocument(new Term("id", "0"), doc(0, "abc_0", 1));
    assertEquals(List.of("abc_1/99"), suggest(1)); // actual: [abc_0/1]
  }

  /** 1 of 10 deleted (the second best), num=2: queue (long) (2 / 0.9) = 2. */
  public void testSecondOfTenDeletedTop2() throws Exception {
    indexOneSegment(10, 100, 1);
    assertEquals(List.of("abc_0/100", "abc_2/98"), suggest(2)); // actual: [abc_0/100]
  }

  /** Control: 5 of 10 deleted gives (long) (1 / 0.5) = 2, one spare slot, and the right answer. */
  public void testHalfDeletedControl() throws Exception {
    indexOneSegment(10, 100, 0, 2, 4, 6, 8);
    assertEquals(List.of("abc_1/99"), suggest(1));
  }
}
