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

import org.apache.lucene.util.VectorUtil;

/**
 * VectorUtil.dotProduct(byte[], byte[]) on two legal arrays whose length is close to
 * Integer.MAX_VALUE. Only the last three elements are non-zero (1 * 1 each), so the exact dot
 * product is 3 and no accumulator can overflow.
 *
 * <p>Run with -Dlucene.useNativeDotProduct=true and a libdotProduct built from
 * lucene/misc/src/c/dotProduct.c on java.library.path (on an SVE machine), and without the flag as
 * the control. Exits 0 iff the result is 3. The length is the first argument or the system property
 * dot.length (default Integer.MAX_VALUE - 2, the largest byte[] the JVM allocates).
 */
public class NativeDotProductNearMaxLength {
  public static void main(String[] args) {
    final int n =
        args.length > 0
            ? Integer.parseInt(args[0])
            : Integer.getInteger("dot.length", Integer.MAX_VALUE - 2);
    final byte[] a = new byte[n];
    final byte[] b = new byte[n];
    for (int i = n - 3; i < n; i++) {
      a[i] = 1;
      b[i] = 1;
    }
    System.out.println(
        "length=" + n + " lucene.useNativeDotProduct=" + System.getProperty("lucene.useNativeDotProduct"));
    System.out.flush();
    final int dot = VectorUtil.dotProduct(a, b);
    System.out.println("dotProduct=" + dot + " expected=3");
    if (dot != 3) {
      System.out.println("FAILURE: wrong dot product");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
