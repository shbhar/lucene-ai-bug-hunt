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

import java.text.ParseException;
import org.apache.lucene.expressions.js.JavascriptCompiler;

/** Release-agnostic main() form of TestHexOctalLiteralOverflow: exits 1 when the bug is present. */
public class HexOctalLiteralOverflowMain {
  public static void main(String[] args) throws Exception {
    int bad = 0;
    for (String s : new String[] {"0x8000000000000000", "01000000000000000000000", "1 + 0xffffffffffffffff"}) {
      try {
        System.out.println("ok    " + s + " = " + JavascriptCompiler.compile(s).evaluate(null));
      } catch (ParseException pe) {
        System.out.println("ok    " + s + " -> ParseException " + pe.getMessage());
      } catch (Exception e) {
        bad++;
        System.out.println("WRONG " + s + " -> " + e);
      }
    }
    if (bad > 0) throw new AssertionError(bad + " literals escaped as an unchecked exception");
  }
}
