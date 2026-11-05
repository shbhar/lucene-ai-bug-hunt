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

import java.util.HashMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.queries.function.valuesource.ConstValueSource;
import org.apache.lucene.queries.function.valuesource.IfFunction;
import org.apache.lucene.queries.function.valuesource.LongFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.mutable.MutableValue;

public class TestGroupByIfFunctionMissingValue extends LuceneTestCase {

  public void testDocWithoutValueGoesToNullGroup() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, newIndexWriterConfig())) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("x", 0));
        w.addDocument(doc);
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IfFunction func =
            new IfFunction(
                new ConstValueSource(1f), new LongFieldSource("x"), new LongFieldSource("x"));
        TopGroups<MutableValue> groups =
            new GroupingSearch(func, new HashMap<>())
                .search(new IndexSearcher(reader), MatchAllDocsQuery.INSTANCE, 0, 10);
        // if(1, x, x) has no value for the document without x, so it belongs in the null group.
        // Expected: 2 groups (0.0 and null); actual: 1 group (0.0) holding both documents.
        assertEquals(2, groups.groups.length);
      }
    }
  }
}
