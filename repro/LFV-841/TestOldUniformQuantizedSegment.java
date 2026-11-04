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
package org.apache.lucene.backward_index;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

/**
 * INDEX_10_3_2 is a zip of an index written by Lucene 10.3.2 with
 * Lucene99HnswScalarQuantizedVectorsFormat (7 bits, default confidence interval): one EUCLIDEAN
 * field "euc7" with 4 dimensions, a stored "id", two segments, no merges.
 *
 * <ul>
 *   <li>segment _0: a0, a1, a2, each [1, 1, 1, 1], so the writer stored the quantiles (1, 1);
 *   <li>segment _1: b0 = [2, 1, 1, 1], b1 = [3, 3, 3, 3], b2 = [-1, 0, 2, 5].
 * </ul>
 *
 * <p>For the query [1, 1, 1, 1], a0..a2 are exact matches; Lucene 10.3.2 ranks them first with
 * score 1.0, b0 (distance 1) next with 0.49.
 */
public class TestOldUniformQuantizedSegment extends LuceneTestCase {

  static final String INDEX_10_3_2 =
      "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";

  public void testExactMatchRanksFirst() throws Exception {
    Path path = createTempDir("old-sq-index");
    TestUtil.unzip(new ByteArrayInputStream(Base64.getDecoder().decode(INDEX_10_3_2)), path);
    try (Directory dir = FSDirectory.open(path);
        DirectoryReader reader = DirectoryReader.open(dir)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      float[] query = {1f, 1f, 1f, 1f};
      TopDocs top = searcher.search(new KnnFloatVectorQuery("euc7", query, 6), 6);
      StringBuilder hits = new StringBuilder();
      for (ScoreDoc sd : top.scoreDocs) {
        String id = searcher.storedFields().document(sd.doc).get("id");
        hits.append(String.format(Locale.ROOT, " %s=%.6g", id, sd.score));
      }
      String first = searcher.storedFields().document(top.scoreDocs[0].doc).get("id");
      assertTrue("an exact match must rank first; hits:" + hits, first.startsWith("a"));
    }
  }
}
