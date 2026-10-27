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
package org.apache.lucene.index.memory;

import java.io.IOException;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.SortedNumericDocValues;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A MemoryIndex that is not frozen accepts new data after it has been searched (freeze() javadoc:
 * "After calling this ... you cannot subsequently add new data"; TestMemoryIndex#testFreezeAPI:
 * "check we can add a new field after searching"). Searching computes per-field state lazily
 * (sorted terms, points min/max and sort order, sorted doc values, norms) and never recomputes
 * it, so adding more values to a field that was already read leaves that field inconsistent: a
 * point query misses, doc values are unsorted, a term lookup throws.
 */
public class TestMemoryIndexAddAfterSearch extends LuceneTestCase {

  private final Analyzer analyzer = new StandardAnalyzer();

  /** Points: min/max and 1D sort order are computed once. */
  public void testPointAddedAfterSearchIsFound() {
    Query exactOne = IntPoint.newExactQuery("p", 1);
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new IntPoint("p", 2), analyzer);
    assertEquals(0f, mi.search(exactOne), 0f); // only 2 is indexed so far
    mi.addField(new IntPoint("p", 1), analyzer); // allowed: the index is not frozen

    MemoryIndex fresh = new MemoryIndex();
    fresh.addField(new IntPoint("p", 2), analyzer);
    fresh.addField(new IntPoint("p", 1), analyzer);
    assertTrue(fresh.search(exactOne) > 0f);
    assertTrue("point value 1 added after the first search is not found", mi.search(exactOne) > 0f);
  }

  /** SORTED_NUMERIC doc values must be returned in sorted order. */
  public void testSortedNumericAddedAfterSearchIsSorted() throws IOException {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new SortedNumericDocValuesField("n", 2), analyzer);
    mi.search(new TermQuery(new Term("other", "x")));
    mi.addField(new SortedNumericDocValuesField("n", 1), analyzer);

    LeafReader reader = (LeafReader) mi.createSearcher().getIndexReader();
    SortedNumericDocValues values = reader.getSortedNumericDocValues("n");
    assertTrue(values.advanceExact(0));
    assertEquals(2, values.docValueCount());
    long first = values.nextValue(), second = values.nextValue();
    assertTrue("values not sorted: " + first + ", " + second, first <= second);
  }

  /** Terms: the term dictionary is sorted in place once and then added to. */
  public void testTermAddedAfterSearchIsFound() {
    MemoryIndex mi = new MemoryIndex();
    mi.addField("f", "b", analyzer);
    assertTrue(mi.search(new TermQuery(new Term("f", "b"))) > 0f);
    mi.addField("f", "a", analyzer);
    assertTrue("term 'a' added after the first search is not found", mi.search(new TermQuery(new Term("f", "a"))) > 0f);
  }

  /** Same cause, other input: the search after the second add throws. */
  public void testTermAddedAfterSearchDoesNotThrow() {
    MemoryIndex mi = new MemoryIndex();
    mi.addField("f", "b d", analyzer);
    assertTrue(mi.search(new TermQuery(new Term("f", "b"))) > 0f);
    mi.addField("f", "a c e", analyzer);
    // ArrayIndexOutOfBoundsException from BytesRefHash.get (AssertionError "bytesID exceeds byteStart len" with -ea)
    float score = mi.search(new TermQuery(new Term("f", "a")));
    assertTrue("term 'a' added after the first search is not found", score > 0f);
  }

  /** Norms are computed once, on the first read of the field's norms. */
  public void testNormAfterLaterValue() throws IOException {
    MemoryIndex mi = new MemoryIndex();
    mi.addField(new TextField("f", "a", Field.Store.NO), analyzer);
    LeafReader reader = (LeafReader) mi.createSearcher().getIndexReader();
    assertTrue(reader.getNormValues("f").advanceExact(0)); // reads (and caches) the norm only
    mi.addField(new TextField("f", "c d e f g h", Field.Store.NO), analyzer);
    float actual = mi.search(new TermQuery(new Term("f", "a")));

    MemoryIndex fresh = new MemoryIndex();
    fresh.addField(new TextField("f", "a", Field.Store.NO), analyzer);
    fresh.addField(new TextField("f", "c d e f g h", Field.Store.NO), analyzer);
    float expected = fresh.search(new TermQuery(new Term("f", "a")));
    assertEquals("score after adding a second value", expected, actual, 0f);
  }
}
