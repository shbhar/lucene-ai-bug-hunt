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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/** Version-neutral variant: each manager's reduce reports the labels of the collectors it got. */
public class TestMultiCollectorManagerSingleNestedLegacy extends LuceneTestCase {

  static final class Labelled extends SimpleCollector {
    final String label;

    Labelled(String label) {
      this.label = label;
    }

    @Override
    public void collect(int doc) {}

    @Override
    public ScoreMode scoreMode() {
      return ScoreMode.COMPLETE_NO_SCORES;
    }
  }

  static final class LabelledManager implements CollectorManager<Labelled, List<String>> {
    final String label;

    LabelledManager(String label) {
      this.label = label;
    }

    @Override
    public Labelled newCollector() {
      return new Labelled(label);
    }

    @Override
    public List<String> reduce(Collection<Labelled> collectors) {
      List<String> labels = new ArrayList<>();
      for (Object c : collectors) {
        labels.add(((Labelled) c).label);
      }
      return labels;
    }
  }

  public void testSingleNested() throws Exception {
    try (Directory dir = newDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig())) {
        w.addDocument(new Document());
      }
      try (DirectoryReader reader = DirectoryReader.open(dir)) {
        IndexSearcher searcher = new IndexSearcher(reader);
        MultiCollectorManager inner =
            new MultiCollectorManager(new LabelledManager("A"), new LabelledManager("B"));
        Object[] direct = searcher.search(new MatchAllDocsQuery(), inner);
        assertEquals("[B]", direct[1].toString());
        Object[] nested =
            (Object[]) searcher.search(new MatchAllDocsQuery(), new MultiCollectorManager(inner))[0];
        assertEquals("[A]", nested[0].toString());
        assertEquals("collectors handed to manager B's reduce", "[B]", nested[1].toString());
      }
    }
  }
}
