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
package org.apache.lucene.search.grouping;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

/**
 * GroupingSearch must order the documents of a group like IndexSearcher#search(Query, int, Sort)
 * orders them for the same Sort on the same searcher. IndexSearcher rewrites the Sort against
 * itself; GroupingSearch passes it on as given.
 */
public class TestGroupingSearchSortRewrite extends LuceneTestCase {

  public void testSortWithinGroupMatchesIndexSearcher() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new StandardAnalyzer()))) {
        for (String body : new String[] {"a a a a a a a a x x", "a"}) { // doc 0, doc 1
          Document doc = new Document();
          doc.add(new TextField("body", body, Field.Store.NO));
          doc.add(new SortedDocValuesField("group", new BytesRef("g")));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new ClassicSimilarity());
        // sort by the score of body:a, best first
        QueryValueSource score = new QueryValueSource(new TermQuery(new Term("body", "a")), 0f);
        Sort sort = new Sort(score.asDoubleValuesSource().getSortField(true));

        TopGroups<BytesRef> grouped =
            new GroupingSearch("group")
                .setSortWithinGroup(sort)
                .setGroupDocsLimit(10)
                .search(searcher, new MatchAllDocsQuery(), 0, 10);
        ScoreDoc[] sorted = searcher.search(new MatchAllDocsQuery(), 10, sort).scoreDocs;

        assertEquals(describe(sorted), describe(grouped.groups[0].scoreDocs()));
      }
    }
  }

  private static String describe(ScoreDoc[] hits) {
    StringBuilder sb = new StringBuilder();
    for (ScoreDoc hit : hits) {
      sb.append("doc").append(hit.doc).append('=').append(((FieldDoc) hit).fields[0]).append(' ');
    }
    return sb.toString().trim();
  }
}
