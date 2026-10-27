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
import org.apache.lucene.document.DoubleDocValuesField;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.function.ValueSource;
import org.apache.lucene.queries.function.valuesource.DivFloatFunction;
import org.apache.lucene.queries.function.valuesource.DoubleFieldSource;
import org.apache.lucene.queries.function.valuesource.IntFieldSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.Sort;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.mutable.MutableValue;
import org.apache.lucene.util.mutable.MutableValueDouble;
import org.apache.lucene.util.mutable.MutableValueFloat;

/**
 * MutableValueFloat and MutableValueDouble compare values with the primitive {@code ==} in
 * equalsSameType, while their compareSameType uses Float/Double.compare and their hashCode uses
 * Float.floatToIntBits / Double.doubleToLongBits (the integer siblings MutableValueInt and
 * MutableValueLong are consistent). So a NaN value is not equal to itself, and +0.0 / -0.0 are
 * equal but hash differently. Grouping by a function relies on equals/hashCode.
 */
public class TestMutableValueFloatingPointEquals extends LuceneTestCase {

  /** Grouping by a double field whose values include NaN. */
  public void testGroupByDoubleFieldWithNaN() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      for (double v : new double[] {Double.NaN, 1.0, Double.NaN}) {
        Document doc = new Document();
        doc.add(new DoubleDocValuesField("v", v));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        w.close();
        assertTwoGroups(new IndexSearcher(reader), new DoubleFieldSource("v"));
      }
    }
  }

  /** The same with no NaN in the index: the function 0 / 0 yields NaN for two documents. */
  public void testGroupByDivisionFunctionYieldingNaN() throws Exception {
    try (Directory dir = newDirectory()) {
      IndexWriter w = new IndexWriter(dir, new IndexWriterConfig());
      int[][] ab = {{0, 0}, {1, 1}, {0, 0}};
      for (int[] p : ab) {
        Document doc = new Document();
        doc.add(new NumericDocValuesField("a", p[0]));
        doc.add(new NumericDocValuesField("b", p[1]));
        w.addDocument(doc);
      }
      w.forceMerge(1);
      try (DirectoryReader reader = DirectoryReader.open(w)) {
        w.close();
        ValueSource ratio = new DivFloatFunction(new IntFieldSource("a"), new IntFieldSource("b"));
        assertTwoGroups(new IndexSearcher(reader), ratio);
      }
    }
  }

  private static void assertTwoGroups(IndexSearcher searcher, ValueSource groupBy)
      throws Exception {
    GroupingSearch grouping = new GroupingSearch(groupBy, new HashMap<>());
    grouping.setGroupSort(Sort.INDEXORDER);
    grouping.setGroupDocsLimit(10);
    grouping.setAllGroups(true);
    // documents 0 and 2 share the value NaN, document 1 has the value 1
    TopGroups<MutableValue> groups = grouping.search(searcher, MatchAllDocsQuery.INSTANCE, 0, 10);
    assertEquals("number of groups", 2, groups.groups.length);
    assertEquals("totalGroupCount", Integer.valueOf(2), groups.totalGroupCount);
    assertEquals("documents in the NaN group", 2, groups.groups[0].scoreDocs().length);
    assertEquals("documents in the group of 1", 1, groups.groups[1].scoreDocs().length);
  }

  /** java.lang.Object#equals: reflexive, and equal objects must have equal hash codes. */
  public void testEqualsContract() {
    MutableValueDouble nanD = new MutableValueDouble();
    nanD.exists = true;
    nanD.value = Double.NaN;
    assertEquals("Double NaN must equal itself", nanD, nanD);

    MutableValueFloat nanF = new MutableValueFloat();
    nanF.exists = true;
    nanF.value = Float.NaN;
    assertEquals("Float NaN must equal itself", nanF, nanF);

    MutableValueDouble posZero = new MutableValueDouble();
    posZero.exists = true;
    posZero.value = 0.0;
    MutableValueDouble negZero = new MutableValueDouble();
    negZero.exists = true;
    negZero.value = -0.0;
    if (posZero.equals(negZero)) {
      assertEquals(
          "equal values (+0.0 and -0.0) must have equal hash codes",
          posZero.hashCode(),
          negZero.hashCode());
    }
  }
}
