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
import org.apache.lucene.document.Field;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Probe for a separate, already-known issue (not this bug): the inner query matches no document at
 * all. Used only to check whether the empty-queue guard of the fix also covers it.
 */
public class TestRescoreTopNQueryEmptyInnerProbe extends LuceneTestCase {

  public void testInnerQueryMatchesNothing() throws IOException {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      Document doc = new Document();
      doc.add(new StringField("f", "a", Field.Store.NO));
      doc.add(new NumericDocValuesField("v", 1));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        Query q =
            new RescoreTopNQuery(
                new TermQuery(new Term("f", "zzz")), DoubleValuesSource.fromLongField("v"), 2);
        assertEquals(0, searcher.search(q, 10).scoreDocs.length);
        assertEquals(0, searcher.count(q));
      }
    }
  }
}
