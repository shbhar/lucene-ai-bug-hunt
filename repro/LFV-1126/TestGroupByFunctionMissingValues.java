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
import org.apache.lucene.document.FloatDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.FloatFieldSource;
import org.apache.lucene.queries.function.valuesource.SumFloatFunction;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.mutable.MutableValue;

public class TestGroupByFunctionMissingValues extends LuceneTestCase {

  public void testMissingFunctionValuesFormOneGroup() throws Exception {
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (float a : new float[] {5, 7}) {
        Document doc = new Document();
        doc.add(new FloatDocValuesField("a", a));
        w.addDocument(doc);
      }
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        ValueSource sum =
            new SumFloatFunction(
                new ValueSource[] {new FloatFieldSource("a"), new FloatFieldSource("b")});
        TopGroups<MutableValue> groups =
            new GroupingSearch(sum, new HashMap<>())
                .search(new IndexSearcher(reader), MatchAllDocsQuery.INSTANCE, 0, 10);
        // expected one null group (b is missing); actual: two groups (-ea: AssertionError)
        assertEquals(1, groups.groups.length);
      }
    }
  }
}
