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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.facet.taxonomy.TaxonomyReader;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.TopScoreDocCollectorManager;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestDrillSidewaysTwoPhaseSecondDim extends LuceneTestCase {

  public void testPhraseDrillDownSequential() throws Exception {
    String[] body = {"x y", "y x", "x y"};
    try (Directory dir = new ByteBuffersDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec());
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        for (int i = 0; i < 40; i++) {
          Document doc = new Document();
          if (i < 2) {
            doc.add(new StringField("a", "1", Field.Store.NO));
          }
          if (i < body.length) {
            doc.add(new TextField("body", body[i], Field.Store.NO));
          }
          w.addDocument(doc);
        }
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        searcher.setQueryCache(null);
        FacetsConfig config = new FacetsConfig();
        DrillDownQuery ddq = new DrillDownQuery(config);
        ddq.add("a", new TermQuery(new Term("a", "1")));
        ddq.add("b", new PhraseQuery("body", "x", "y"));
        TopDocs hits =
            new DrillSideways(searcher, config, (TaxonomyReader) null)
                .search(
                    ddq,
                    new TopScoreDocCollectorManager(10, Integer.MAX_VALUE),
                    List.of(new FacetsCollectorManager(), new FacetsCollectorManager()))
                .drillDownResult();
        // expected: [0] (only doc 0 has a:1 and the phrase "x y"); actual: [0, 1]
        assertEquals(List.of(0), Arrays.stream(hits.scoreDocs).map(sd -> sd.doc).toList());
      }
    }
  }
}
