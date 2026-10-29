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
package org.apache.lucene.luke.models.search;

import java.util.Optional;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestNextPagePastLastPage extends LuceneTestCase {

  // Expected: nextPage() past the last full page is empty; actual: a 0-hit page with offset 0.
  public void testNextPageAfterLastPage() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 1010; i++) {
          w.addDocument(new Document());
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Search search = new SearchFactory().newInstance(reader);
        search.search(
            new MatchAllDocsQuery(), new SimilarityConfig.Builder().build(), null, 10, false);
        for (int page = 1; page < 101; page++) {
          assertTrue(search.nextPage().isPresent());
        }
        Optional<SearchResults> pastEnd = search.nextPage();
        assertTrue(
            "got a page: size="
                + pastEnd.map(SearchResults::size).orElse(-1)
                + " offset="
                + pastEnd.map(SearchResults::getOffset).orElse(-1),
            pastEnd.isEmpty());
      }
    }
  }
}
