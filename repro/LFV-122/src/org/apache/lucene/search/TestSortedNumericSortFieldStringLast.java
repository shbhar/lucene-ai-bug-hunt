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

import java.io.IOException;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * The SortedNumericSortField class javadoc says: "Like sorting by string, this also supports sorting
 * missing values as first or last, via SortField#STRING_FIRST or SortField#STRING_LAST." The
 * constructor accepts SortField.STRING_LAST as the missing value, but sorting with it (and using it
 * as an index sort) throws ClassCastException.
 */
public class TestSortedNumericSortFieldStringLast extends LuceneTestCase {

  private static final SortField SORT_FIELD =
      new SortedNumericSortField(
          "f", SortField.Type.LONG, false, SortedNumericSelector.Type.MIN, SortField.STRING_LAST);

  public void testSearchMissingLast() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      w.addDocument(new Document()); // doc 0: no value
      Document d1 = new Document();
      d1.add(new SortedNumericDocValuesField("f", 2));
      w.addDocument(d1); // doc 1
      Document d2 = new Document();
      d2.add(new SortedNumericDocValuesField("f", 1));
      w.addDocument(d2); // doc 2
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        TopFieldDocs td =
            new IndexSearcher(reader).search(MatchAllDocsQuery.INSTANCE, 3, new Sort(SORT_FIELD));
        // missing values last: 1 (doc 2), 2 (doc 1), then the doc without a value
        assertEquals(2, td.scoreDocs[0].doc);
        assertEquals(1, td.scoreDocs[1].doc);
        assertEquals(0, td.scoreDocs[2].doc);
      }
    }
  }

  public void testIndexSortMissingLast() throws IOException {
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig().setIndexSort(new Sort(SORT_FIELD));
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        w.addDocument(new Document());
        Document d1 = new Document();
        d1.add(new SortedNumericDocValuesField("f", 1));
        w.addDocument(d1);
        w.commit();
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        assertEquals(new Sort(SORT_FIELD), reader.leaves().get(0).reader().getMetaData().sort());
      }
    }
  }
}
