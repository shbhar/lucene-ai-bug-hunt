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
package org.apache.lucene.benchmark.quality.trec;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Map;
import org.apache.lucene.benchmark.quality.QualityQuery;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTrecJudgeNegativeGrade extends LuceneTestCase {

  public void testNegativeGradeIsNotRelevant() throws Exception {
    TrecJudge judge = new TrecJudge(new BufferedReader(new StringReader("1 0 d -2\n")));
    // expected grade -2 non-relevant (trec_eval: relevant only if grade >= 1); actual relevant
    assertFalse(
        "expected isRelevant=false for qrels grade -2, got true",
        judge.isRelevant("d", new QualityQuery("1", Map.of())));
  }
}
