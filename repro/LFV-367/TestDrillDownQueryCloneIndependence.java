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
package org.apache.lucene.facet.verify;

import org.apache.lucene.document.Document;
import org.apache.lucene.facet.DrillDownQuery;
import org.apache.lucene.facet.FacetsConfig;
import org.apache.lucene.facet.sortedset.SortedSetDocValuesFacetField;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.MatchAllDocsQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.index.RandomIndexWriter;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check (public API only, different package from org.apache.lucene.facet): a
 * DrillDownQuery clone must not change what the source query matches, and vice versa, when a value
 * is OR'd into a dimension that both already constrain.
 *
 * <p>Index: doc0 color=red shape=circle, doc1 color=blue shape=circle, doc2 color=green
 * shape=square.
 */
public class TestDrillDownQueryCloneIndependence extends LuceneTestCase {

  private Directory dir;
  private IndexReader reader;
  private IndexSearcher searcher;
  private FacetsConfig config;

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    config = new FacetsConfig();
    RandomIndexWriter w = new RandomIndexWriter(random(), dir);
    String[][] docs = {{"red", "circle"}, {"blue", "circle"}, {"green", "square"}};
    for (String[] d : docs) {
      Document doc = new Document();
      doc.add(new SortedSetDocValuesFacetField("color", d[0]));
      doc.add(new SortedSetDocValuesFacetField("shape", d[1]));
      w.addDocument(config.build(doc));
    }
    reader = w.getReader();
    w.close();
    searcher = newSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  private int count(DrillDownQuery q) throws Exception {
    return searcher.count(q);
  }

  /** Control: the oracle values, built without any clone. */
  public void testControlNoClone() throws Exception {
    DrillDownQuery red = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    red.add("color", "red");
    assertEquals(1, count(red));
    DrillDownQuery redOrBlue = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    redOrBlue.add("color", "red");
    redOrBlue.add("color", "blue");
    assertEquals(2, count(redOrBlue));
    DrillDownQuery redOrGreen = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    redOrGreen.add("color", "red");
    redOrGreen.add("color", "green");
    assertEquals(2, count(redOrGreen));
  }

  /** Control (the case upstream TestDrillDownQuery#testClone covers): a NEW dim on the clone. */
  public void testControlCloneAddsNewDimension() throws Exception {
    DrillDownQuery original = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    original.add("color", "red");
    DrillDownQuery clone = original.clone();
    clone.add("shape", "square");
    assertEquals("clone: red AND square", 0, count(clone));
    assertEquals("original: red", 1, count(original));
  }

  /** Claim A: OR-ing a value into an existing dim of the clone changes the original. */
  public void testCloneAddToExistingDimLeaksIntoOriginal() throws Exception {
    DrillDownQuery original = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    original.add("color", "red");
    DrillDownQuery clone = original.clone();
    clone.add("color", "blue");
    assertEquals("clone: red OR blue", 2, count(clone));
    assertEquals("original must still be color=red only; original=" + original, 1, count(original));
  }

  /** Claim B: OR-ing a value into an existing dim of the original changes an earlier clone. */
  public void testOriginalAddToExistingDimLeaksIntoEarlierClone() throws Exception {
    DrillDownQuery original = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    original.add("color", "red");
    DrillDownQuery snapshot = original.clone();
    original.add("color", "blue");
    assertEquals("original: red OR blue", 2, count(original));
    assertEquals("snapshot must still be color=red only; snapshot=" + snapshot, 1, count(snapshot));
  }

  /** Same as claim A through the expert add(String, Query) API. */
  public void testCloneAddCustomQueryToExistingDimLeaksIntoOriginal() throws Exception {
    String field = config.getDimConfig("color").indexFieldName;
    DrillDownQuery original = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    original.add("color", new TermQuery(DrillDownQuery.term(field, "color", "red")));
    DrillDownQuery clone = original.clone();
    clone.add("color", new TermQuery(DrillDownQuery.term(field, "color", "blue")));
    assertEquals("clone: red OR blue", 2, count(clone));
    assertEquals("original must still be color=red only; original=" + original, 1, count(original));
  }

  /**
   * If the original was already executed before clone(), its cached built dim query hides the
   * shared-builder change at first; the leaked value appears once the original's dim is touched
   * again (here: OR-ing green into it).
   */
  public void testLeakSurfacesLaterWhenOriginalWasAlreadyBuilt() throws Exception {
    DrillDownQuery original = new DrillDownQuery(config, MatchAllDocsQuery.INSTANCE);
    original.add("color", "red");
    assertEquals(1, count(original)); // builds and caches the dim query of the original
    DrillDownQuery clone = original.clone();
    clone.add("color", "blue");
    assertEquals("clone: red OR blue", 2, count(clone));
    assertEquals("original: red (cached)", 1, count(original));
    original.add("color", "green");
    assertEquals(
        "original must be red OR green (2 docs), blue was only added to the clone; original="
            + original,
        2,
        count(original));
  }

  /** Sanity: Term import is used by the expert test; keep the oracle term construction honest. */
  public void testControlTermField() throws Exception {
    String field = config.getDimConfig("color").indexFieldName;
    assertEquals(1, searcher.count(new TermQuery(DrillDownQuery.term(field, "color", "red"))));
    assertEquals(0, searcher.count(new TermQuery(new Term(field, "no-such-term"))));
  }
}
