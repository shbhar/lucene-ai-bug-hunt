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
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.MultiReader;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A reader with a leaf whose maxDoc() is 0 cannot be searched: IndexSearcher's default slicing
 * wraps every leaf in LeafReaderContextPartition.createForEntireSegment, whose constructor rejects
 * minDocId (0) >= maxDoc (0). ParallelLeafReader explicitly supports zero sub-readers (maxDoc 0).
 * Public API only.
 */
public class TestSearchZeroDocLeaf extends LuceneTestCase {

  public void testControlEmptyMultiReader() throws Exception {
    // a reader with no leaves at all is fine
    IndexSearcher s = new IndexSearcher(new MultiReader());
    assertEquals(0, s.count(new MatchAllDocsQuery()));
  }

  public void testZeroDocLeafAlone() throws Exception {
    try (ParallelLeafReader empty = new ParallelLeafReader(true)) {
      assertEquals(0, empty.maxDoc());
      IndexSearcher s = new IndexSearcher(empty);
      assertEquals(0, s.count(new MatchAllDocsQuery()));
      assertEquals(0, s.search(new MatchAllDocsQuery(), 10).scoreDocs.length);
    }
  }

  public void testZeroDocLeafNextToARealIndex() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (int i = 0; i < 5; i++) {
          Document doc = new Document();
          doc.add(new StringField("f", "a", Field.Store.NO));
          w.addDocument(doc);
        }
      }
      try (DirectoryReader real = DirectoryReader.open(dir);
          ParallelLeafReader empty = new ParallelLeafReader(true);
          MultiReader both = new MultiReader(new IndexReader[] {real, empty}, false)) {
        Query q = new TermQuery(new Term("f", "a"));
        assertEquals(5, new IndexSearcher(real).count(q));
        IndexSearcher s = new IndexSearcher(both);
        assertEquals(5, s.count(q));
        assertEquals(5, s.search(q, 10).scoreDocs.length);
      }
    }
  }
}
