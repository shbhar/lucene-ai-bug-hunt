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

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * A MultiCollectorManager that wraps exactly one manager whose collectors are themselves
 * MultiCollectors (for example another MultiCollectorManager) hands the wrong collectors to that
 * manager's reduce. newCollector() returns MultiCollector.wrap(c), which is c itself when there is
 * one collector; reduce() then sees a MultiCollector and unwraps it as if it were its own. Public
 * API only.
 */
public class TestMultiCollectorManagerSingleNested extends LuceneTestCase {

  private Directory dir;
  private DirectoryReader reader;
  private IndexSearcher searcher;
  private final Query query = new TermQuery(new Term("f", "a"));

  @Override
  public void setUp() throws Exception {
    super.setUp();
    dir = newDirectory();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
      for (int i = 0; i < 10; i++) {
        Document doc = new Document();
        doc.add(new StringField("f", "a", Field.Store.NO));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    reader = DirectoryReader.open(dir);
    searcher = new IndexSearcher(reader);
  }

  @Override
  public void tearDown() throws Exception {
    reader.close();
    dir.close();
    super.tearDown();
  }

  /** Control: the inner manager on its own. */
  public void testControlInnerAlone() throws Exception {
    MultiCollectorManager inner =
        new MultiCollectorManager(
            new TopScoreDocCollectorManager(1, Integer.MAX_VALUE),
            new TopScoreDocCollectorManager(5, Integer.MAX_VALUE));
    Object[] r = searcher.search(query, inner);
    assertEquals(1, ((TopDocs) r[0]).scoreDocs.length);
    assertEquals(5, ((TopDocs) r[1]).scoreDocs.length);
  }

  /** Same top-k managers, nested once: the second result silently loses its hits. */
  public void testSingleNestedSilentlyWrong() throws Exception {
    MultiCollectorManager inner =
        new MultiCollectorManager(
            new TopScoreDocCollectorManager(1, Integer.MAX_VALUE),
            new TopScoreDocCollectorManager(5, Integer.MAX_VALUE));
    Object[] r = (Object[]) searcher.search(query, new MultiCollectorManager(inner))[0];
    assertEquals(1, ((TopDocs) r[0]).scoreDocs.length);
    assertEquals("top-5 result of the nested manager", 5, ((TopDocs) r[1]).scoreDocs.length);
  }

  /** Different managers, nested once: the hit counter's reduce receives a top-k collector. */
  public void testSingleNestedMixedManagers() throws Exception {
    MultiCollectorManager inner =
        new MultiCollectorManager(
            new TopScoreDocCollectorManager(5, Integer.MAX_VALUE),
            new TotalHitCountCollectorManager(searcher.getSlices()));
    Object[] r = (Object[]) searcher.search(query, new MultiCollectorManager(inner))[0];
    assertEquals(5, ((TopDocs) r[0]).scoreDocs.length);
    assertEquals(10, ((Integer) r[1]).intValue());
  }
}
