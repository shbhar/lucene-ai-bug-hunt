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

import org.apache.lucene.expressions.Expression;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValues;

/** Release-agnostic main() form of TestBooleanOperandLongNarrowing: exits 1 when the bug is present. */
public class BooleanOperandLongNarrowingMain {
  static double eval(String source, double... vars) throws Exception {
    Expression expr = JavascriptCompiler.compile(source);
    DoubleValues[] values = new DoubleValues[expr.variables.length];
    for (int i = 0; i < values.length; i++) {
      final double v = vars[i];
      values[i] =
          new DoubleValues() {
            @Override
            public double doubleValue() {
              return v;
            }

            @Override
            public boolean advanceExact(int doc) {
              return true;
            }
          };
    }
    return expr.evaluate(values);
  }

  public static void main(String[] args) throws Exception {
    double flags = 0x1p32 + 1;
    String[][] cases = {
      {"flags & 0x100000000", "4.294967296E9"},
      {"(flags & 0x100000000) != 0", "1.0"},
      {"(flags & 0x100000000) ? 1 : 0", "1.0"},
      {"!(flags & 0x100000000)", "0.0"},
      {"(flags & 0x100000000) && 1", "1.0"},
      {"(flags & 0x100000000) || 0", "1.0"},
    };
    int bad = 0;
    for (String[] c : cases) {
      double got = eval(c[0], flags);
      boolean ok = Double.toString(got).equals(c[1]);
      if (!ok) bad++;
      System.out.println((ok ? "ok   " : "WRONG") + " " + c[0] + " = " + got + " (expected " + c[1] + ")");
    }
    if (bad > 0) throw new AssertionError(bad + " wrong results");
  }
}
