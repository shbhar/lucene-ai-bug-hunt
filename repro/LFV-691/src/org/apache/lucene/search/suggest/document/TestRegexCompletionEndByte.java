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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.lucene104.Lucene104Codec;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestRegexCompletionEndByte extends LuceneTestCase {

  public void testDotNeedsARealCharacter() throws Exception {
    IndexWriterConfig iwc = new IndexWriterConfig(new StandardAnalyzer());
    iwc.setCodec(
        new Lucene104Codec() {
          final PostingsFormat completion = new Completion104PostingsFormat();

          @Override
          public PostingsFormat getPostingsFormatForField(String field) {
            return completion;
          }
        });
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w = new IndexWriter(dir, iwc)) {
      w.addDocument(List.of(new SuggestField("suggest", "foo", 2)));
      w.addDocument(List.of(new SuggestField("suggest", "food", 1)));
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        RegexCompletionQuery query = new RegexCompletionQuery(new Term("suggest", "foo."));
        TopSuggestDocs hits = new SuggestIndexSearcher(reader).suggest(query, 10, false);
        List<String> keys =
            Arrays.stream(hits.scoreLookupDocs()).map(hit -> hit.key.toString()).toList();
        // expected: [food], the only suggestion with 4 characters; actual: [foo, food]
        assertEquals(List.of("food"), keys);
      }
    }
  }
}
