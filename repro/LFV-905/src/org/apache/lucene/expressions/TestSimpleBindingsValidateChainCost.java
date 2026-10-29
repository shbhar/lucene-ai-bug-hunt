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
package org.apache.lucene.expressions;

import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleBindingsValidateChainCost extends LuceneTestCase {

  public void testValidateOfChainCostsAboutAsMuchAsResolvingEachBinding() throws Exception {
    int n = 500;
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("x0", DoubleValuesSource.constant(1));
    for (int i = 1; i < n; i++) {
      bindings.add("x" + i, JavascriptCompiler.compile("x" + (i - 1) + " + 1"));
    }
    long start = System.nanoTime();
    for (int i = 0; i < n; i++) {
      bindings.getDoubleValuesSource("x" + i);
    }
    long resolveEach = System.nanoTime() - start;
    start = System.nanoTime();
    bindings.validate();
    long validate = System.nanoTime() - start;
    // Expected: validate() within 10x of resolving each binding once (as on 8.5.2); actual ~100x.
    assertTrue(
        "validate() took "
            + validate / 1_000_000
            + " ms, resolving each binding once took "
            + resolveEach / 1_000_000
            + " ms",
        validate <= 10 * resolveEach);
  }
}
