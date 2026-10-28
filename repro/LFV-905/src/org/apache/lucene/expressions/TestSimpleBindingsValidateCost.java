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

import java.text.ParseException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleBindings.validate() "Traverses the graph of bindings, checking there are no cycles or missing
 * references". It re-expands the whole sub-graph below every binding once per origin and once per
 * path, and copies the current path into a new set at every step. On a graph whose expressions share
 * sub-expressions this is exponential; on a plain chain it is cubic. Only public API is used.
 */
public class TestSimpleBindingsValidateCost extends LuceneTestCase {

  /** Delegates to a compiled expression and counts how often a source is built from it. */
  private static final class CountingExpression extends Expression {
    private final Expression in;
    private final AtomicLong instantiations;

    CountingExpression(Expression in, AtomicLong instantiations) {
      super(in.sourceText, in.variables);
      this.in = in;
      this.instantiations = instantiations;
    }

    @Override
    public double evaluate(DoubleValues[] functionValues) {
      try {
        return in.evaluate(functionValues);
      } catch (RuntimeException e) {
        throw e;
      } catch (Exception e) { // evaluate declares IOException since 9.10
        throw new RuntimeException(e);
      }
    }

    @Override
    public DoubleValuesSource getDoubleValuesSource(Bindings bindings) {
      instantiations.incrementAndGet();
      return super.getDoubleValuesSource(bindings);
    }
  }

  /**
   * A chain of 16 "diamonds" (X(i+1) = L(i) + R(i), L(i) = X(i) + 1, R(i) = X(i) * 2; 49 bindings):
   * a traversal needs at most one visit per binding and origin (49^2 = 2401 instantiations); this one
   * instantiates the shared sub-expressions once per path.
   */
  public void testValidateWithSharedSubExpressionsIsPolynomial() throws Exception {
    int depth = 16;
    AtomicLong instantiations = new AtomicLong();
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("X0", DoubleValuesSource.constant(3));
    for (int i = 0; i < depth; i++) {
      bindings.add(
          "L" + i, new CountingExpression(JavascriptCompiler.compile("X" + i + " + 1"), instantiations));
      bindings.add(
          "R" + i, new CountingExpression(JavascriptCompiler.compile("X" + i + " * 2"), instantiations));
      bindings.add(
          "X" + (i + 1),
          new CountingExpression(
              JavascriptCompiler.compile("L" + i + " + R" + i), instantiations));
    }
    int numBindings = 3 * depth + 1;
    bindings.validate();
    long bound = (long) numBindings * numBindings;
    assertTrue(
        "validate() instantiated expression bindings "
            + instantiations.get()
            + " times for a graph of "
            + numBindings
            + " bindings (expected at most "
            + bound
            + ")",
        instantiations.get() <= bound);
  }

  /**
   * Wall-clock check (coarse on purpose): a plain chain x(i) = x(i-1) + 1 of 2000 bindings, with no
   * shared sub-expression. validate() took 99 ms on Lucene 8.5.2 and 39-43 s on 8.6.0 and main on the
   * machine where this was written, because every step copies the whole current path into a new set.
   */
  public void testValidateOfALinearChainIsNotCubic() throws ParseException {
    int n = 2000;
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("x0", DoubleValuesSource.constant(1));
    for (int i = 1; i <= n; i++) {
      bindings.add("x" + i, JavascriptCompiler.compile("x" + (i - 1) + " + 1"));
    }
    long start = System.nanoTime();
    bindings.validate();
    long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
    assertTrue(
        "validate() of a " + n + "-binding chain took " + ms + " ms (expected well under 5000 ms)",
        ms < 5000);
  }
}
