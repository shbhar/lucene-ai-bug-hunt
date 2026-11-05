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

import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestSimpleBindingsSharedSubExpressionCost extends LuceneTestCase {

  /** Delegates to a compiled expression and counts how often a source is built from it. */
  private static final class CountingExpression extends Expression {
    private final Expression in;
    private final AtomicLong builds;

    CountingExpression(String sourceText, AtomicLong builds) throws Exception {
      this(JavascriptCompiler.compile(sourceText), builds);
    }

    private CountingExpression(Expression in, AtomicLong builds) {
      super(in.sourceText, in.variables);
      this.in = in;
      this.builds = builds;
    }

    @Override
    public double evaluate(DoubleValues[] functionValues) throws IOException {
      return in.evaluate(functionValues);
    }

    @Override
    public DoubleValuesSource getDoubleValuesSource(Bindings bindings) {
      builds.incrementAndGet();
      return super.getDoubleValuesSource(bindings);
    }
  }

  public void testGetDoubleValuesSourceIsPolynomialInTheNumberOfBindings() throws Exception {
    // a chain of 16 diamonds: X(i+1) = L(i) + R(i), L(i) = X(i) + 1, R(i) = X(i) * 2
    int depth = 16;
    int numBindings = 3 * depth + 1; // 49
    AtomicLong builds = new AtomicLong();
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("X0", DoubleValuesSource.constant(3));
    for (int i = 0; i < depth; i++) {
      bindings.add("L" + i, new CountingExpression("X" + i + " + 1", builds));
      bindings.add("R" + i, new CountingExpression("X" + i + " * 2", builds));
      bindings.add("X" + (i + 1), new CountingExpression("L" + i + " + R" + i, builds));
    }
    bindings.getDoubleValuesSource("X" + depth);
    // expected: each expression binding is built a bounded number of times (at most 49^2 = 2401);
    // actual: one build per path through the graph, 3 * (2^16 - 1) = 196605
    long bound = (long) numBindings * numBindings;
    assertTrue(
        "getDoubleValuesSource(\"X"
            + depth
            + "\") instantiated expression bindings "
            + builds.get()
            + " times for a graph of "
            + numBindings
            + " bindings (expected at most "
            + bound
            + ")",
        builds.get() <= bound);
  }
}
