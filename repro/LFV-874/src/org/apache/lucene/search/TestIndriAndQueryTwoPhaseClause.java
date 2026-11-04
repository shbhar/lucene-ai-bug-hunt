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

import java.util.Arrays;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestIndriAndQueryTwoPhaseClause extends LuceneTestCase {

  public void testHitsMatchAtLeastOneClause() throws Exception {
    try (Directory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        for (long n : new long[] {1, 50}) {
          Document doc = new Document();
          doc.add(new SortedNumericDocValuesField("n", n));
          w.addDocument(doc);
        }
        Document doc = new Document();
        doc.add(new StringField("body", "c", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        Query query =
            new IndriAndQuery(
                List.of(
                    new BooleanClause(
                        SortedNumericDocValuesField.newSlowRangeQuery("n", 1, 10), Occur.SHOULD),
                    new BooleanClause(new TermQuery(new Term("body", "c")), Occur.SHOULD)));
        List<Integer> hits =
            Arrays.stream(new IndexSearcher(reader).search(query, 10).scoreDocs)
                .map(hit -> hit.doc)
                .sorted()
                .toList();
        // doc 0 (n=1) matches the range, doc 2 matches body:c; doc 1 (n=50) matches neither
        assertEquals(List.of(0, 2), hits);
      }
    }
  }
}
