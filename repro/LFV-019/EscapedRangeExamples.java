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

import java.util.regex.Pattern;
import org.apache.lucene.util.automaton.CharacterRunAutomaton;
import org.apache.lucene.util.automaton.Operations;
import org.apache.lucene.util.automaton.RegExp;

/**
 * Plain-Java table of character classes, lucene-core only. For each pattern it prints how many of
 * the 95 printable ASCII characters (U+0020..U+007E) Lucene's RegExp accepts, next to
 * java.util.regex, and lists the accepted characters when there are few. Exit code 0 always: this
 * is a report, not a test.
 */
public class EscapedRangeExamples {
  static final String[] PATTERNS = {
    "[.-z]", // control: unescaped range start
    "[\\.-z]", // escaped range start
    "[!-~]", // control: printable ASCII except space
    "[\\!-\\~]", // the same range, both ends escaped
    "[a-\\~]", // control: escaped range end
    "[\\+-0]", // the independent reviewer's case
    "[0-9]",
    "[\\0-\\9]", // what RegExp.toString() prints for [0-9]
    "[!--]", // control: range ending in '-'
    "[\\!--]", // the same range, start escaped
    "[\\-]", // escaped dash alone (TestRegExpParsing.testEscapedDashCharClass)
    "[.-]", // trailing dash, unescaped start
    "[\\.-]", // trailing dash, escaped start
    "[\\\\-z]", // escaped backslash as range start (never a range, also not in 10.1.0)
    "[\\~-!]", // escaped start, end below start
  };

  public static void main(String[] args) {
    System.out.println("RegExp(\"[0-9]\").toString() = " + new RegExp("[0-9]").toString());
    System.out.printf("%-12s %-22s %-22s %s%n", "pattern", "Lucene RegExp", "java.util.regex", "Lucene accepts");
    for (String p : PATTERNS) {
      String lucene;
      String accepted = "";
      try {
        CharacterRunAutomaton r =
            new CharacterRunAutomaton(
                Operations.determinize(
                    new RegExp(p).toAutomaton(), Operations.DEFAULT_DETERMINIZE_WORK_LIMIT));
        int n = 0;
        StringBuilder sb = new StringBuilder();
        for (char c = 0x20; c <= 0x7E; c++) {
          if (r.run(String.valueOf(c))) {
            n++;
            sb.append(c);
          }
        }
        lucene = n + " of 95";
        accepted = n <= 12 ? "{" + String.join(" ", sb.toString().split("")) + "}" : "'" + sb.charAt(0) + "'..'" + sb.charAt(sb.length() - 1) + "'";
      } catch (IllegalArgumentException e) {
        lucene = "throws IAE";
        accepted = e.getMessage();
      }
      String jdk;
      try {
        Pattern jp = Pattern.compile(p);
        int n = 0;
        for (char c = 0x20; c <= 0x7E; c++) {
          if (jp.matcher(String.valueOf(c)).matches()) n++;
        }
        jdk = n + " of 95";
      } catch (RuntimeException e) {
        jdk = "throws";
      }
      System.out.printf("%-12s %-22s %-22s %s%n", p, lucene, jdk, accepted);
    }
    System.out.println("syntax flags NONE, [\\.-z] accepts 'a': "
        + new CharacterRunAutomaton(Operations.determinize(new RegExp("[\\.-z]", RegExp.NONE).toAutomaton(),
            Operations.DEFAULT_DETERMINIZE_WORK_LIMIT)).run("a"));
    System.out.println("parse tree of [\\.-z]:");
    System.out.print(new RegExp("[\\.-z]").toStringTree());
  }
}
