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
package org.apache.lucene.search;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.tests.util.TestUtil;

public class TestRescoreTopNQueryEmptyVectorSegment extends LuceneTestCase {

  public void testSegmentWithVectorFieldButNoVectors() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w =
            new IndexWriter(dir, new IndexWriterConfig().setCodec(TestUtil.getDefaultCodec()))) {
      Document doc = new Document();
      doc.add(new StringField("id", "0", Field.Store.NO));
      doc.add(new KnnFloatVectorField("v", new float[] {1, 0}));
      w.addDocument(doc);
      w.addDocument(new Document());
      w.deleteDocuments(new Term("id", "0"));
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        assertEquals(0, reader.leaves().get(0).reader().getFloatVectorValues("v").size());
        Query query =
            RescoreTopNQuery.createFullPrecisionRescorerQuery(
                MatchAllDocsQuery.INSTANCE, new float[] {1, 0}, "v", 1);
        // expected: 1 hit with score 0 (it has no vector); actual: UnsupportedOperationException
        TopDocs topDocs = new IndexSearcher(reader).search(query, 1);
        assertEquals(1, topDocs.scoreDocs.length);
        assertEquals(0f, topDocs.scoreDocs[0].score, 0f);
      }
    }
  }
}
