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
 * Limit of the one-line rounding fix: the deleted document has TWO completions that both outrank
 * every live one (abc_x/100 and abc_y/100). Rounding up gives one spare queue slot, which holds
 * only one of the two rejected completions. Expected to fail both on stock main and with the
 * Math.ceil fix; a fix that re-runs the search while TopResults.isComplete is false would pass it.
 */
public class TestSuggestFewDeletionsLimit extends LuceneTestCase {

  private static final String FIELD = "suggest_field";

  public void testDeletedDocWithTwoTopCompletions() throws Exception {
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.KEYWORD, false);
    Directory dir = newDirectory();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
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
    IndexWriter writer = new IndexWriter(dir, iwc);
    Document doc0 = new Document();
    doc0.add(new StringField("id", "0", Field.Store.NO));
    doc0.add(new SuggestField(FIELD, "abc_x", 100));
    doc0.add(new SuggestField(FIELD, "abc_y", 100));
    writer.addDocument(doc0);
    for (int i = 1; i < 10; i++) {
      Document doc = new Document();
      doc.add(new StringField("id", Integer.toString(i), Field.Store.NO));
      doc.add(new SuggestField(FIELD, "abc_" + i, 100 - i));
      writer.addDocument(doc);
    }
    writer.commit();
    writer.deleteDocuments(new Term("id", "0"));
    try (DirectoryReader reader = DirectoryReader.open(writer)) {
      assertEquals(1, reader.leaves().size());
      assertEquals(9, reader.numDocs());
      SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
      PrefixCompletionQuery query = new PrefixCompletionQuery(analyzer, new Term(FIELD, "abc_"));
      TopSuggestDocs top = searcher.suggest(query, 1, false);
      List<String> hits = new ArrayList<>();
      for (TopSuggestDocs.SuggestScoreDoc hit : top.scoreLookupDocs()) {
        hits.add(hit.key + "/" + (long) hit.score);
      }
      assertEquals(List.of("abc_1/99"), hits);
    }
    writer.close();
    dir.close();
    analyzer.close();
  }
}
