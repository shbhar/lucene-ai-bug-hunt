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

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;
import org.junit.runner.Description;
import org.junit.runner.Request;

public class TestVectorsAboveSimilarityRuns {

  @Test
  public void testRunnerListsTestVectorsAboveSimilarity() {
    List<String> names =
        Request.aClass(TestFloatVectorSimilarityQuery.class)
            .getRunner()
            .getDescription()
            .getChildren()
            .stream()
            .map(Description::getMethodName)
            .sorted()
            .toList();
    // expected: the runner lists testVectorsAboveSimilarity; actual: 18 test methods, not this one
    assertTrue(
        "the runner lists " + names.size() + " test methods and no testVectorsAboveSimilarity: "
            + names,
        names.contains("testVectorsAboveSimilarity"));
  }
}
