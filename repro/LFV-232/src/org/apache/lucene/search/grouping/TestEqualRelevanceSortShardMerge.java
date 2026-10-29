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

import java.util.ArrayList;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.document.SortedDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;

public class TestEqualRelevanceSortShardMerge extends LuceneTestCase {

  public void testMergedGroupKeepsBestHit() throws Exception {
    try (Directory dir = new ByteBuffersDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setMergePolicy(NoMergePolicy.INSTANCE))) {
      for (float score : new float[] {1, 4}) { // one segment per document, each searched as a shard
        Document doc = new Document();
        doc.add(new SortedDocValuesField("group", new BytesRef("g")));
        doc.add(new FeatureField("features", "score", score));
        w.addDocument(doc);
        w.flush();
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        Query query = FeatureField.newLinearQuery("features", "score", 1f);
        Sort withinGroupSort = new Sort(SortField.FIELD_SCORE); // equals Sort.RELEVANCE
        GroupingSearch grouping =
            new GroupingSearch("group").setSortWithinGroup(withinGroupSort).setGroupDocsLimit(1);
        List<TopGroups<BytesRef>> shards = new ArrayList<>();
        for (LeafReaderContext leaf : reader.leaves()) {
          shards.add(grouping.search(new IndexSearcher(leaf.reader()), query, 0, 10));
        }
        TopGroups<BytesRef> merged =
            TopGroups.merge(
                shards, Sort.RELEVANCE, withinGroupSort, 0, 1, TopGroups.ScoreMergeMode.None);
        // expected: group g's top hit is shard 1's score-4 document; actual: shard 0's score-1 document
        assertEquals("shard of group g's top hit", 1, merged.groups[0].scoreDocs()[0].shardIndex);
      }
    }
  }
}
