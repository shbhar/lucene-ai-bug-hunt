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
package org.apache.lucene.queryparser.flexible.core.processors;

import java.text.NumberFormat;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.queryparser.flexible.standard.config.PointsConfig;
import org.apache.lucene.queryparser.flexible.standard.processors.PointQueryNodeProcessor;
import org.apache.lucene.search.PointRangeQuery;
import org.apache.lucene.search.Query;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestPipelineListIteratorSetConfigHandler extends LuceneTestCase {

  public void testProcessorSetThroughListIteratorGetsConfigHandler() throws Exception {
    StandardQueryParser parser = new StandardQueryParser();
    parser.setPointsConfigMap(
        Map.of("n", new PointsConfig(NumberFormat.getIntegerInstance(Locale.ROOT), Integer.class)));
    ListIterator<QueryNodeProcessor> it =
        ((QueryNodeProcessorPipeline) parser.getQueryNodeProcessor()).listIterator();
    while ((it.next() instanceof PointQueryNodeProcessor) == false) {}
    it.set(new PointQueryNodeProcessor());
    Query q = parser.parse("n:5", "f");
    // expected: point query, as with pipeline.set(k, p); actual: TermQuery (p has no config)
    assertTrue(
        "expected a PointRangeQuery, got " + q.getClass().getName() + " " + q,
        q instanceof PointRangeQuery);
  }
}
