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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.HashMap;
import java.util.Map;
import org.apache.lucene.expressions.SimpleBindings;
import org.apache.lucene.expressions.js.JavascriptCompiler;
import org.apache.lucene.search.DoubleValuesSource;

/** Release-agnostic main(): exits 1 when value sources of "f(x)" with different f are equal. */
public class ExpressionValueSourceEqualsFunctionsMain {
  public static double negate(double v) {
    return -v;
  }

  public static void main(String[] args) throws Exception {
    MethodType mt = MethodType.methodType(double.class, double.class);
    Map<String, MethodHandle> a = new HashMap<>(JavascriptCompiler.DEFAULT_FUNCTIONS);
    a.put("f", MethodHandles.publicLookup().findStatic(Math.class, "abs", mt));
    Map<String, MethodHandle> b = new HashMap<>(JavascriptCompiler.DEFAULT_FUNCTIONS);
    b.put("f", MethodHandles.publicLookup().findStatic(ExpressionValueSourceEqualsFunctionsMain.class, "negate", mt));
    SimpleBindings bindings = new SimpleBindings();
    bindings.add("x", DoubleValuesSource.fromLongField("x"));
    DoubleValuesSource sa = JavascriptCompiler.compile("f(x)", a).getDoubleValuesSource(bindings);
    DoubleValuesSource sb = JavascriptCompiler.compile("f(x)", b).getDoubleValuesSource(bindings);
    System.out.println("equals=" + sa.equals(sb) + " sameHash=" + (sa.hashCode() == sb.hashCode()));
    if (sa.equals(sb)) throw new AssertionError("f=abs and f=negate value sources are equal");
  }
}
