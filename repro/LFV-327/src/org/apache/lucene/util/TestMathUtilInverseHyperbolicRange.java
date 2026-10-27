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
package org.apache.lucene.util;

import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * MathUtil.asinh/acosh/atanh evaluate the textbook closed forms directly:
 *
 * <ul>
 *   <li>asinh(a) = log(sqrt(a*a + 1) + a) and acosh(a) = log(sqrt(a*a - 1) + a): a*a overflows for
 *       every finite |a| >= 2^512, so both return Infinity although the true values are at most
 *       about 710.5;
 *   <li>asinh(a) and atanh(a) = 0.5 * log((1 + a) / (1 - a)): the log argument rounds to exactly
 *       1.0 for 0 < |a| <= 2^-53 (asinh) and 0 < |a| <= 2^-54 (atanh), so both return zero although
 *       the true value is about a.
 * </ul>
 *
 * Reference values: asinh(2^512) = 513 ln 2 = 355.58450362725193; asinh(1e200) = acosh(1e200) =
 * ln(2e200) = 461.2101657793691; asinh(x) = x - x^3/6 + ... and atanh(x) = x + x^3/3 + ..., so
 * both are 1e-17 for x = 1e-17 (to double precision). Tolerances are loose (relative 1e-12): the
 * failures are Infinity and 0, not rounding.
 */
public class TestMathUtilInverseHyperbolicRange extends LuceneTestCase {

  private static void assertClose(String what, double expected, double actual) {
    assertTrue(what + ": expected ~" + expected + " but was " + actual, Double.isFinite(actual));
    assertEquals(what, expected, actual, Math.abs(expected) * 1e-12);
  }

  public void testAsinhLargeFiniteArgument() {
    assertClose("asinh(2^512)", 355.58450362725193, MathUtil.asinh(0x1p512));
    assertClose("asinh(1e200)", 461.2101657793691, MathUtil.asinh(1e200));
    assertClose("asinh(-1e200)", -461.2101657793691, MathUtil.asinh(-1e200));
  }

  public void testAcoshLargeFiniteArgument() {
    assertClose("acosh(2^512)", 355.58450362725193, MathUtil.acosh(0x1p512));
    assertClose("acosh(1e200)", 461.2101657793691, MathUtil.acosh(1e200));
  }

  public void testAsinhTinyNonZeroArgument() {
    assertClose("asinh(1e-17)", 1e-17, MathUtil.asinh(1e-17));
    assertClose("asinh(-1e-17)", -1e-17, MathUtil.asinh(-1e-17));
  }

  public void testAtanhTinyNonZeroArgument() {
    assertClose("atanh(1e-17)", 1e-17, MathUtil.atanh(1e-17));
    assertClose("atanh(-1e-17)", -1e-17, MathUtil.atanh(-1e-17));
  }
}
