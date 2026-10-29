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
package historyprobe;

import com.ibm.icu.lang.UCharacter;
import com.ibm.icu.lang.UScript;
import com.ibm.icu.util.VersionInfo;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.icu.segmentation.DefaultICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizer;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerConfig;
import org.apache.lucene.analysis.icu.segmentation.ICUTokenizerFactory;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;

/**
 * Version-portable main() port of TestMyanmarSyllableDropsLetters for Lucene 5.0 and later (no-arg
 * ICUTokenizer + setReader). Same three inputs and the same oracle: a letter or digit of the input
 * that lies in no token's offsets is lost. The syllable mode is reached by reflection, so the same
 * source compiles on releases that predate the myanmarAsWords option; there it reports API_ABSENT.
 *
 * <p>Exit code: 1 if the syllable mode (config constructor or factory option) loses a letter in any
 * input; 0 otherwise (including API_ABSENT).
 */
public class MyanmarSyllableHistoryProbe {

  static final String[] INPUTS = {
    "\u1000\uA9E6", // MYANMAR LETTER KA + MYANMAR MODIFIER LETTER SHAN REDUPLICATION
    "\u1000\uAA70", // MYANMAR LETTER KA + MYANMAR MODIFIER LETTER KHAMTI REDUPLICATION
    "\u1000\u1031\u102C\u1004\u103A\u1038 \u00B5m" // 'kaung:' + space + MICRO SIGN + m
  };

  static String esc(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); ) {
      int cp = s.codePointAt(i);
      if (cp > 0x20 && cp < 0x7F) {
        sb.appendCodePoint(cp);
      } else {
        sb.append(String.format("<U+%04X>", cp));
      }
      i += Character.charCount(cp);
    }
    return sb.toString();
  }

  /** Runs t over text; appends the tokens to toks and returns the letters/digits in no token. */
  static List<String> lost(Tokenizer t, String text, List<String> toks) throws Exception {
    boolean[] covered = new boolean[text.length()];
    t.setReader(new StringReader(text));
    OffsetAttribute off = t.addAttribute(OffsetAttribute.class);
    CharTermAttribute term = t.addAttribute(CharTermAttribute.class);
    t.reset();
    while (t.incrementToken()) {
      toks.add(esc(term.toString()));
      for (int i = off.startOffset(); i < off.endOffset(); i++) {
        covered[i] = true;
      }
    }
    t.end();
    t.close();
    List<String> out = new ArrayList<>();
    for (int i = 0; i < text.length(); ) {
      int cp = text.codePointAt(i);
      if (Character.isLetterOrDigit(cp) && covered[i] == false) {
        out.add(String.format("U+%04X", cp));
      }
      i += Character.charCount(cp);
    }
    return out;
  }

  static Object classpathLoader() throws Exception {
    String[] names = {
      "org.apache.lucene.util.ClasspathResourceLoader", // 9.0+
      "org.apache.lucene.analysis.util.ClasspathResourceLoader" // before 9.0
    };
    for (String n : names) {
      try {
        Class<?> c = Class.forName(n);
        return c.getConstructor(Class.class).newInstance(MyanmarSyllableHistoryProbe.class);
      } catch (ClassNotFoundException e) {
        // try the next location
      }
    }
    throw new IllegalStateException("no ClasspathResourceLoader");
  }

  static Tokenizer factoryTokenizer() throws Exception {
    Map<String, String> args = new HashMap<>();
    args.put("myanmarAsWords", "false");
    ICUTokenizerFactory f = new ICUTokenizerFactory(args); // throws IAE if the option is unknown
    Object loader = classpathLoader();
    for (Method m : ICUTokenizerFactory.class.getMethods()) {
      if (m.getName().equals("inform") && m.getParameterCount() == 1) {
        m.invoke(f, loader);
      }
    }
    return f.create();
  }

  public static void main(String[] args) throws Exception {
    System.out.println(
        "lucene="
            + org.apache.lucene.util.Version.LATEST
            + " icu4j="
            + VersionInfo.ICU_VERSION
            + " unicode="
            + UCharacter.getUnicodeVersion());
    for (int cp : new int[] {0xA9E6, 0xAA70, 0x00B5}) {
      System.out.println(
          String.format(
              "  U+%04X icu: gc=%d script=%s defined=%s",
              cp,
              UCharacter.getType(cp),
              UScript.getShortName(UScript.getScript(cp)),
              UCharacter.isDefined(cp)));
    }

    Constructor<DefaultICUTokenizerConfig> ctor = null;
    try {
      ctor = DefaultICUTokenizerConfig.class.getConstructor(boolean.class, boolean.class);
    } catch (NoSuchMethodException e) {
      // option not present in this release
    }
    String factoryStatus = "ok";
    try {
      factoryTokenizer().close();
    } catch (IllegalArgumentException e) {
      factoryStatus = "rejected: " + e.getMessage();
    }
    System.out.println(
        "API: DefaultICUTokenizerConfig(boolean,boolean) "
            + (ctor == null ? "ABSENT" : "present")
            + "; ICUTokenizerFactory myanmarAsWords=false "
            + factoryStatus);

    boolean dropped = false;
    for (String in : INPUTS) {
      List<String> dt = new ArrayList<>();
      List<String> dl = lost(new ICUTokenizer(), in, dt);
      System.out.println("input " + esc(in));
      System.out.println("  default mode        tokens=" + dt + " lost=" + dl);
      if (ctor != null) {
        ICUTokenizerConfig cfg = ctor.newInstance(true, false);
        List<String> st = new ArrayList<>();
        List<String> sl = lost(new ICUTokenizer(cfg), in, st);
        System.out.println("  syllable (config)   tokens=" + st + " lost=" + sl);
        dropped |= sl.isEmpty() == false;
      }
      if (factoryStatus.equals("ok")) {
        List<String> ft = new ArrayList<>();
        List<String> fl = lost(factoryTokenizer(), in, ft);
        System.out.println("  syllable (factory)  tokens=" + ft + " lost=" + fl);
        dropped |= fl.isEmpty() == false;
      }
    }
    if (ctor == null && factoryStatus.equals("ok") == false) {
      System.out.println("RESULT: API_ABSENT (no myanmarAsWords option in this release)");
      return;
    }
    System.out.println("RESULT: " + (dropped ? "BUG_REPRODUCED (syllable mode loses letters)" : "NO_LOSS"));
    if (dropped) {
      System.exit(1);
    }
  }
}
