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
package org.apache.lucene.search.highlight;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.LeafMetaData;
import org.apache.lucene.index.ParallelLeafReader;
import org.apache.lucene.index.Terms;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.search.SortField;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.util.LuceneTestCase;

/** TermVectorLeafReader (public, public constructor) must report usable LeafMetaData. */
public class TestTermVectorLeafReaderMetaData extends LuceneTestCase {

  private static DirectoryReader indexOneDocWithTermVectors(Directory dir) throws Exception {
    FieldType type = new FieldType(TextField.TYPE_NOT_STORED);
    type.setStoreTermVectors(true);
    type.setStoreTermVectorPositions(true);
    type.setStoreTermVectorOffsets(true);
    try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig(new MockAnalyzer(random())))) {
      Document doc = new Document();
      doc.add(new Field("body", "hello world", type));
      w.addDocument(doc);
    }
    return DirectoryReader.open(dir);
  }

  public void testGetMetaData() throws Exception {
    try (Directory dir = newDirectory();
        DirectoryReader r = indexOneDocWithTermVectors(dir)) {
      Terms vector = r.termVectors().get(0, "body");
      TermVectorLeafReader reader = new TermVectorLeafReader("body", vector);
      LeafMetaData metaData = reader.getMetaData(); // throws IllegalArgumentException on main
      assertNull(metaData.sort());
    }
  }

  public void testParallelLeafReaderAndSortedSearch() throws Exception {
    try (Directory dir = newDirectory();
        DirectoryReader r = indexOneDocWithTermVectors(dir)) {
      Terms vector = r.termVectors().get(0, "body");
      TermVectorLeafReader reader = new TermVectorLeafReader("body", vector);
      ParallelLeafReader parallel = new ParallelLeafReader(false, reader); // throws on main
      assertEquals(1, parallel.maxDoc());
      parallel.close();
      IndexSearcher searcher = new IndexSearcher(reader);
      assertEquals(
          1,
          searcher.search(new MatchAllDocsQuery(), 1, new Sort(SortField.FIELD_DOC)).scoreDocs.length);
    }
  }
}
