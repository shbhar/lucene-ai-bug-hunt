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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.charfilter.HTMLStripCharFilterFactory;
import org.apache.lucene.analysis.core.KeywordTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestSuggestFieldAnalyzedEndByte extends LuceneTestCase {

  public void testAnalyzedEndByteHidesOtherSuggestion() throws Exception {
    Analyzer analyzer =
        CustomAnalyzer.builder()
            .addCharFilter(HTMLStripCharFilterFactory.class)
            .withTokenizer(KeywordTokenizerFactory.class)
            .build();
    IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
    iwc.setCodec(TestUtil.alwaysPostingsFormat(new Completion104PostingsFormat()));
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter writer = new IndexWriter(dir, iwc)) {
      writer.addDocument(List.of(new SuggestField("suggest", "a", 1)));
      writer.addDocument(List.of(new SuggestField("suggest", "a&#0;", 2)));
      try (DirectoryReader reader = DirectoryReader.open(writer)) {
        TopSuggestDocs hits =
            new SuggestIndexSearcher(reader)
                .suggest(new PrefixCompletionQuery(analyzer, new Term("suggest", "a")), 10, false);
        // expected: both docs ("a&#0;" weight 2, then "a" weight 1); actual: only doc 1, "a" is hidden
        assertEquals(List.of(1, 0), Arrays.stream(hits.scoreLookupDocs()).map(d -> d.doc).toList());
      }
    }
  }
}
