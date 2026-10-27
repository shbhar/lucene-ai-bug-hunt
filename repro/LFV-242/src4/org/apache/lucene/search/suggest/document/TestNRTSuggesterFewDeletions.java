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
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * A segment with a small fraction of deleted documents: NRTSuggester sizes the TopNSearcher queue
 * as (long) (topN / liveDocsRatio), which truncates back to topN, so a deleted completion at the
 * last result slot ends the search and a live completion is never returned.
 */
public class TestNRTSuggesterFewDeletions extends LuceneTestCase {

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    Codec def = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(def.getName(), def) {
          final PostingsFormat pf = new Completion912PostingsFormat();

          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                return field.equals("suggest_field")
                    ? pf
                    : ((PerFieldPostingsFormat) delegate.postingsFormat()).getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  /** 10 docs in one segment, weights 100..91; deletes the ids given. */
  private DirectoryReader index(Directory dir, Analyzer analyzer, int... deleteIds) throws Exception {
    IndexWriter w = new IndexWriter(dir, iwc(analyzer));
    for (int i = 0; i < 10; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", "" + i, Field.Store.NO));
      doc.add(new SuggestField("suggest_field", "abc_" + i, 100 - i));
      w.addDocument(doc);
    }
    w.commit();
    for (int id : deleteIds) {
      w.deleteDocuments(new Term("id", "" + id));
    }
    w.commit();
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    assertEquals(1, r.leaves().size());
    assertEquals(10 - deleteIds.length, r.numDocs());
    return r;
  }

  private static List<String> keys(TopSuggestDocs td) {
    List<String> l = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : td.scoreLookupDocs()) {
      l.add(sd.key.toString());
    }
    return l;
  }

  /** The best completion's document is deleted: num=1 returns nothing. */
  public void testTopHitDeleted() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer, 0);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    PrefixCompletionQuery q = new PrefixCompletionQuery(analyzer, new Term("suggest_field", "abc_"));
    for (boolean skipDuplicates : new boolean[] {false, true}) {
      assertEquals(List.of("abc_1"), keys(s.suggest(q, 1, skipDuplicates)));
    }
    r.close();
    dir.close();
  }

  /** The second best completion's document is deleted: num=2 returns one hit. */
  public void testSecondHitDeleted() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer, 1);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    PrefixCompletionQuery q = new PrefixCompletionQuery(analyzer, new Term("suggest_field", "abc_"));
    assertEquals(List.of("abc_0", "abc_2"), keys(s.suggest(q, 2, false)));
    r.close();
    dir.close();
  }

  /** Control: half of the documents deleted (the case upstream tests cover) works. */
  public void testHalfDeletedControl() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random());
    Directory dir = newDirectory();
    DirectoryReader r = index(dir, analyzer, 0, 2, 4, 6, 8);
    SuggestIndexSearcher s = new SuggestIndexSearcher(r);
    PrefixCompletionQuery q = new PrefixCompletionQuery(analyzer, new Term("suggest_field", "abc_"));
    assertEquals(List.of("abc_1"), keys(s.suggest(q, 1, false)));
    r.close();
    dir.close();
  }
}
