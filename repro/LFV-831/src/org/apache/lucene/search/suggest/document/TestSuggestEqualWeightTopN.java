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
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * Three documents hold the same suggestion "c" with weights 1, 1 and 2 in one segment. Ranked by
 * score and then by the smaller doc id (the tie-break of TopSuggestDocsCollector#get and of the
 * reference in TestSuggestField#testRandom), top-2 is [doc 2, doc 0] and top-3 is [2, 0, 1].
 * SuggestIndexSearcher returns [2, 1] for n=2, so top-2 is not a prefix of top-3.
 */
public class TestSuggestEqualWeightTopN extends LuceneTestCase {

  /** The newest completion postings format of the running Lucene version (public SPI names). */
  private static PostingsFormat completionFormat() {
    for (String name :
        new String[] {
          "Completion104", "Completion101", "Completion912", "Completion99", "Completion90"
        }) {
      try {
        return PostingsFormat.forName(name);
      } catch (IllegalArgumentException _) {
        // not in this version
      }
    }
    throw new AssertionError("no completion postings format found");
  }

  private static IndexWriterConfig iwc(Analyzer analyzer) {
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setMergePolicy(NoMergePolicy.INSTANCE);
    final PostingsFormat completion = completionFormat();
    final Codec delegate = TestUtil.getDefaultCodec();
    iwc.setCodec(
        new FilterCodec(delegate.getName(), delegate) {
          @Override
          public PostingsFormat postingsFormat() {
            return new PerFieldPostingsFormat() {
              @Override
              public PostingsFormat getPostingsFormatForField(String field) {
                if (field.equals("suggest_field")) {
                  return completion;
                }
                return ((PerFieldPostingsFormat) delegate.postingsFormat())
                    .getPostingsFormatForField(field);
              }
            };
          }
        });
    return iwc;
  }

  private static List<Integer> docs(SuggestIndexSearcher searcher, int n) throws Exception {
    PrefixCompletionQuery query =
        new PrefixCompletionQuery(new KeywordAnalyzer(), new Term("suggest_field", "c"));
    List<Integer> docs = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc sd : searcher.suggest(query, n, false).scoreLookupDocs()) {
      docs.add(sd.doc);
    }
    return docs;
  }

  public void testEqualWeightsOfOneSuggestionFollowDocIdOrder() throws Exception {
    Analyzer analyzer = new KeywordAnalyzer();
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, iwc(analyzer))) {
        int[] weights = {1, 1, 2};
        for (int weight : weights) {
          Document doc = new Document();
          doc.add(new SuggestField("suggest_field", "c", weight));
          w.addDocument(doc);
        }
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(1, reader.leaves().size());
        SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
        List<Integer> top2 = docs(searcher, 2);
        List<Integer> top3 = docs(searcher, 3);
        assertEquals("top-3 by score, then smaller doc id", List.of(2, 0, 1), top3);
        assertEquals(
            "top-2 must be the first two of top-3 (score, then smaller doc id); top-3=" + top3,
            List.of(2, 0),
            top2);
      }
    }
  }
}
