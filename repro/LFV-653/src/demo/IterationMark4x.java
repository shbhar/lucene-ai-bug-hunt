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
package demo;

import java.io.StringReader;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;

/** Lucene 4.x API variant (Reader constructor, one-argument config): exits 1 if U+3005 is dropped. */
public class IterationMark4x {
  public static void main(String[] args) throws Exception {
    ICUTokenizer t = new ICUTokenizer(new StringReader("\u4EBA\u3005"), new DefaultICUTokenizerConfig(false));
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    StringBuilder all = new StringBuilder();
    while (t.incrementToken()) all.append('[').append(term).append(']');
    t.end();
    t.close();
    boolean dropped = all.indexOf("\u3005") < 0;
    System.out.println((dropped ? "DROPPED" : "ok") + " tokens=" + all.toString().replace("\u4EBA", "U+4EBA").replace("\u3005", "U+3005"));
    System.exit(dropped ? 1 : 0);
  }
}
