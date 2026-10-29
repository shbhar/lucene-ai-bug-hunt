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
package org.apache.lucene.search.similarities;

import org.apache.lucene.search.FieldStats;
import org.apache.lucene.search.TermStats;
import org.apache.lucene.tests.util.LuceneTestCase;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.SmallFloat;

public class TestAxiomaticF2ScoreMonotonicInFreq extends LuceneTestCase {

  public void testScoreDoesNotDecreaseWhenFreqReachesPowerOfTwo() {
    float lo = Math.nextDown(0x1p29f), hi = 0x1p29f;
    Similarity.SimScorer exp =
        new AxiomaticF2EXP()
            .scorer(
                1f,
                new FieldStats("f", 611, 611, 937_166_864_135L, 1200),
                new TermStats(new BytesRef("t"), 589, 1_610_613_290L));
    Similarity.SimScorer log =
        new AxiomaticF2LOG()
            .scorer(
                1f,
                new FieldStats("f", 644, 644, 944_092_194_137L, 1226),
                new TermStats(new BytesRef("t"), 582, 1_610_613_283L));
    long expNorm = SmallFloat.intToByte4(1_073_741_848);
    long logNorm = SmallFloat.intToByte4(805_306_392);
    float expLo = exp.score(lo, expNorm), expHi = exp.score(hi, expNorm);
    float logLo = log.score(lo, logNorm), logHi = log.score(hi, logNorm);
    // expected score(freq 2^29) >= score(freq 2^29 - 32) (SimScorer#score); actual 1 ulp lower
    assertTrue(
        "expected no drop, got F2EXP " + expLo + " -> " + expHi + ", F2LOG " + logLo + " -> "
            + logHi,
        expHi >= expLo && logHi >= logLo);
  }
}
