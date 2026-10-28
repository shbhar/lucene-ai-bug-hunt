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
import java.util.concurrent.atomic.AtomicLong;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;
import org.apache.lucene.search.DoubleValuesSource;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * SimpleBindings resolves an Expression binding again for every reference to it, so a binding graph
 * in which several expressions share a sub-expression is expanded into a tree: getDoubleValuesSource()
 * builds one ExpressionValueSource per path through the graph, a number that grows exponentially
 * with the depth of the sharing (validate() has its own, separate cost problem).
 *
 * <p>The graph is a chain of "diamonds": X(i+1) = L(i) + R(i), L(i) = X(i) + 1, R(i) = X(i) * 2,
 * with X0 a plain DoubleValuesSource. Depth 16 is 49 bindings. Every expression is wrapped so that
 * we can count how many times SimpleBindings instantiates it (calls getDoubleValuesSource on it).
 * Only public API is used.
 */
public class TestSimpleBindingsSharedSubExpressionCost extends LuceneTestCase {

  private static final int DEPTH = 16;

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

  private static SimpleBindings diamondChain(int depth, AtomicLong instantiations)
      throws ParseException {
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
    return bindings;
  }

  /**
   * Building the source of the top binding should instantiate every expression binding a bounded
   * number of times; today every reference re-instantiates the referenced expression, so the source
   * of X16 is a tree with one ExpressionValueSource per path (3 * (2^16 - 1) of them).
   */
  public void testGetDoubleValuesSourceIsPolynomialInTheNumberOfBindings() throws Exception {
    AtomicLong instantiations = new AtomicLong();
    SimpleBindings bindings = diamondChain(DEPTH, instantiations);
    int numBindings = 3 * DEPTH + 1;
    DoubleValuesSource top = bindings.getDoubleValuesSource("X" + DEPTH);
    assertNotNull(top);
    long bound = (long) numBindings * numBindings;
    assertTrue(
        "getDoubleValuesSource(\"X"
            + DEPTH
            + "\") instantiated expression bindings "
            + instantiations.get()
            + " times for a graph of "
            + numBindings
            + " bindings (expected at most "
            + bound
            + ")",
        instantiations.get() <= bound);
  }
}
