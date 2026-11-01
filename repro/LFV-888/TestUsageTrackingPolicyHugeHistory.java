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

import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.time.Duration;
import org.apache.lucene.index.Term;
import org.junit.jupiter.api.Test;

public class TestUsageTrackingPolicyHugeHistory {

  @Test
  public void testSecondDistinctQueryReturns() {
    // expected: the size is rejected up front or both onUse calls return; actual (-da): onUse(b) spins forever
    assertTimeoutPreemptively(
        Duration.ofSeconds(30),
        () -> {
          UsageTrackingQueryCachingPolicy policy;
          try {
            policy = new UsageTrackingQueryCachingPolicy(715_827_883);
          } catch (IllegalArgumentException _) {
            return;
          }
          policy.onUse(new PrefixQuery(new Term("f", "a")));
          policy.onUse(new PrefixQuery(new Term("f", "b")));
        },
        "onUse must return");
  }
}
