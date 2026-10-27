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
package org.apache.lucene.analysis.hunspell;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.apache.lucene.store.ByteBuffersDirectory;

/** Version-portable variant (releases with the Hunspell class): exits 1 on a wrong result or an error. */
public class CompoundPatternReplacementMain {
  static Hunspell load(String aff, String dic) throws Exception {
    return new Hunspell(
        new Dictionary(
            new ByteBuffersDirectory(),
            "hunspell",
            new ByteArrayInputStream(aff.getBytes(StandardCharsets.UTF_8)),
            new ByteArrayInputStream(dic.getBytes(StandardCharsets.UTF_8))));
  }

  public static void main(String[] args) throws Exception {
    Hunspell h2 =
        load(
            "COMPOUNDFLAG A\nCHECKCOMPOUNDPATTERN 2\nCHECKCOMPOUNDPATTERN o b z\nCHECKCOMPOUNDPATTERN oo ba u\nCOMPOUNDMIN 1\n",
            "2\nfoo/A\nbar/A\n");
    boolean fozar = h2.spell("fozar"), barfozar = h2.spell("barfozar");
    String auu;
    try {
      auu =
          String.valueOf(
              load("COMPOUNDFLAG x\nCOMPOUNDMIN 1\nCHECKCOMPOUNDPATTERN 1\nCHECKCOMPOUNDPATTERN u u u\n", "1\nau/x\n")
                  .spell("auu"));
    } catch (StackOverflowError e) {
      auu = "StackOverflowError";
    }
    System.out.println("fozar=" + fozar + " (expected true) barfozar=" + barfozar + " (expected true) auu=" + auu + " (expected false)");
    if (!fozar || !barfozar || !auu.equals("false")) {
      System.out.println("FAIL");
      System.exit(1);
    }
    System.out.println("OK");
  }
}
