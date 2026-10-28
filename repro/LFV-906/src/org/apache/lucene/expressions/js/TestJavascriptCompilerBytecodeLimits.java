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
package org.apache.lucene.expressions.js;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.text.ParseException;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * JavascriptCompiler.compile documents two failure modes for an expression it cannot compile:
 * ParseException ("on failure to compile") and IllegalStateException ("if the resulting expression
 * class fails to link (e.g., complexity)"); IllegalArgumentException is documented only for a
 * function with a wrong signature. These well-formed, shallowly nested expressions are too big for
 * one JVM method. Compiling them must either succeed or fail with one of the documented exceptions,
 * not with an exception of the bytecode library.
 */
public class TestJavascriptCompilerBytecodeLimits extends LuceneTestCase {

  /**
   * (x+x)+(x+x)... with n leaves: nesting depth about 2*log2(n) (29 for n = 10,000), far below
   * the default limit 1024
   */
  private static String balancedSum(int lo, int hi) {
    if (lo == hi) {
      return "x";
    }
    int mid = (lo + hi) >>> 1;
    return "(" + balancedSum(lo, mid) + "+" + balancedSum(mid + 1, hi) + ")";
  }

  /** a complete binary decision tree written as nested ternaries, as for a learned tree model */
  private static String decisionTree(int level, int depth, int[] leaf) {
    if (level == depth) {
      return (leaf[0]++ % 97) + ".25";
    }
    return "(f"
        + (level % 8)
        + " < 0."
        + (1 + level % 9)
        + " ? "
        + decisionTree(level + 1, depth, leaf)
        + " : "
        + decisionTree(level + 1, depth, leaf)
        + ")";
  }

  private static void assertCompilesOrFailsAsDocumented(
      String source, Map<String, MethodHandle> functions) {
    try {
      JavascriptCompiler.compile(source, functions);
    } catch (ParseException | IllegalStateException documented) {
      // documented failure modes
    } catch (RuntimeException e) {
      throw new AssertionError(
          "compile() threw an undocumented "
              + e.getClass().getName()
              + " for a well-formed expression of "
              + source.length()
              + " chars: "
              + e.getMessage(),
          e);
    }
  }

  /** 10,000 additions of one variable (about 70 KB of bytecode for a 64 KB method limit) */
  public void testShallowLongSum() {
    assertCompilesOrFailsAsDocumented(balancedSum(0, 9_999), JavascriptCompiler.DEFAULT_FUNCTIONS);
  }

  /** a depth-12 decision tree of ternaries over 8 variables (nesting depth 26) */
  public void testDecisionTree() {
    assertCompilesOrFailsAsDocumented(
        decisionTree(0, 12, new int[1]), JavascriptCompiler.DEFAULT_FUNCTIONS);
  }

  public static double sum(double[] values) {
    double s = 0;
    for (double v : values) {
      s += v;
    }
    return s;
  }

  /**
   * Nested calls of a custom 127-argument function (the javadoc allows up to 256 arguments): the
   * operand stack gets about 35,000 slots deep while the method stays below 64 KB.
   */
  public void testDeepOperandStackOfWideCustomFunction() throws Exception {
    Map<String, MethodHandle> functions = new HashMap<>(JavascriptCompiler.DEFAULT_FUNCTIONS);
    functions.put(
        "wide",
        MethodHandles.publicLookup()
            .findStatic(
                TestJavascriptCompilerBytecodeLimits.class,
                "sum",
                MethodType.methodType(double.class, double[].class))
            .asCollector(double[].class, 127));
    String source = "x";
    for (int level = 0; level < 140; level++) {
      source = "wide(" + "1,".repeat(126) + source + ")";
    }
    assertCompilesOrFailsAsDocumented(source, functions);
  }
}
