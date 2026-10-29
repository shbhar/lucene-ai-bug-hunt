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
package org.apache.lucene.facet.range;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.facet.FacetResult;
import org.apache.lucene.facet.FacetsCollector;
import org.apache.lucene.facet.FacetsCollectorManager;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestDoubleRangeSignedZero extends LuceneTestCase {

  public void testNegativeZeroCountedInRangeStartingAtZero() throws Exception {
    ByteBuffersDirectory dir = new ByteBuffersDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
    Document doc = new Document();
    doc.add(new DoubleDocValuesField("f", -0.0));
    w.addDocument(doc);
    w.close();
    DirectoryReader r = DirectoryReader.open(dir);
    FacetsCollector fc =
        new IndexSearcher(r).search(MatchAllDocsQuery.INSTANCE, new FacetsCollectorManager());
    DoubleRange range = new DoubleRange("[0, 1]", 0.0, true, 1.0, true);
    assertTrue(range.accept(-0.0));
    FacetResult result = new DoubleRangeFacetCounts("f", fc, range).getAllChildren("f");
    r.close();
    dir.close();
    // expected: the -0.0 document that the range accepts is counted once; actual: count 0
    assertEquals(
        "facet count of [0, 1] for one document with value -0.0",
        1,
        result.labelValues[0].value.intValue());
  }
}
