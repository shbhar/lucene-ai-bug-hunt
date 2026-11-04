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
package org.apache.lucene.facet;

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.sortedset.DefaultSortedSetDocValuesReaderState;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.queries.function.valuesource.QueryValueSource;
import org.apache.lucene.search.FieldDoc;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.ClassicSimilarity;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFacetSortedSearchRewritesSort extends LuceneTestCase {

  public void testSortedHelpersMatchIndexSearcher() throws Exception {
    FacetsConfig config = new FacetsConfig();
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (String body : new String[] {"a a a a a a a a x x", "a"}) {
          Document doc = new Document();
          doc.add(new TextField("body", body, Field.Store.NO));
          doc.add(new SortedSetDocValuesFacetField("color", "red"));
          w.addDocument(config.build(doc));
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setSimilarity(new ClassicSimilarity());
        // sort by the score of body:a under the searcher's Similarity, highest first
        Sort sort =
            new Sort(
                new QueryValueSource(new TermQuery(new Term("body", "a")), 0f)
                    .asDoubleValuesSource()
                    .getSortField(true));
        DrillDownQuery query = new DrillDownQuery(config);
        query.add("color", "red");

        String expected = hits(searcher.search(query, 10, sort));
        String facetsHelper =
            hits(
                FacetsCollectorManager.search(
                        searcher, query, 10, sort, new FacetsCollectorManager())
                    .topDocs());
        DrillSideways ds =
            new DrillSideways(
                searcher, config, new DefaultSortedSetDocValuesReaderState(reader, config));
        String drillSideways = hits(ds.search(query, null, null, 10, sort, false).hits);
        assertEquals(List.of(expected, expected), List.of(facetsHelper, drillSideways));
      }
    }
  }

  private static String hits(TopDocs topDocs) {
    List<String> hits = new ArrayList<>();
    for (ScoreDoc sd : topDocs.scoreDocs) {
      hits.add("doc" + sd.doc + "=" + ((FieldDoc) sd).fields[0]);
    }
    return hits.toString();
  }
}
