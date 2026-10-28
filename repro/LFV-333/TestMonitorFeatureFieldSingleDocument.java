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
package org.apache.lucene.monitor;

import java.io.IOException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.FeatureField;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Monitor matches one document with a MemoryIndex and two or more with an IndexWriter-built reader
 * (DocumentBatch). The same FeatureField document must get the same score either way.
 */
public class TestMonitorFeatureFieldSingleDocument extends LuceneTestCase {

  private static Document doc() {
    Document doc = new Document();
    doc.add(new FeatureField("features", "pagerank", 10f));
    return doc;
  }

  public void testLinearScoreDependsOnBatchSize() throws IOException {
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), Presearcher.NO_FILTERING)) {
      monitor.register(
          new MonitorQuery("linear", FeatureField.newLinearQuery("features", "pagerank", 1f)));
      ScoringMatch inBatch =
          monitor
              .match(new Document[] {doc(), doc()}, ScoringMatch.DEFAULT_MATCHER)
              .matches("linear", 0);
      assertEquals("two-document batch (IndexWriter)", 10f, inBatch.getScore(), 0f);
      ScoringMatch alone = monitor.match(doc(), ScoringMatch.DEFAULT_MATCHER).matches("linear");
      assertNotNull(alone);
      assertEquals("one document (MemoryIndex)", 10f, alone.getScore(), 0f);
    }
  }

  public void testSaturationMatchDroppedForOneDocument() throws IOException {
    try (Monitor monitor = new Monitor(new StandardAnalyzer(), Presearcher.NO_FILTERING)) {
      monitor.register(
          new MonitorQuery(
              "saturation", FeatureField.newSaturationQuery("features", "pagerank", 1f, 10f)));
      ScoringMatch inBatch =
          monitor
              .match(new Document[] {doc(), doc()}, ScoringMatch.DEFAULT_MATCHER)
              .matches("saturation", 0);
      assertEquals("two-document batch (IndexWriter)", 0.5f, inBatch.getScore(), 1e-6f);
      // ScoringMatch reports a match only when its score is > 0
      assertNotNull(
          "one document (MemoryIndex): no match reported",
          monitor.match(doc(), ScoringMatch.DEFAULT_MATCHER).matches("saturation"));
    }
  }
}
