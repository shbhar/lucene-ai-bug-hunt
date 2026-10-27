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
package org.apache.lucene.classification;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.NoMergePolicy;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.analysis.MockAnalyzer;
import org.apache.lucene.tests.analysis.MockTokenizer;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Reach of the BooleanPerceptronClassifier FST input-order bug for a security assessment.
 *
 * <p>The classifier keys its weights by String (UTF-16 order) and adds them to a BYTE1 FST in that
 * order. FSTCompiler requires UTF-8 byte order. The orders disagree when one term has a
 * supplementary character (UTF-8 lead byte F0..F4) and another a character in U+E000..U+FFFF (lead
 * byte EE..EF) at the first differing position.
 *
 * <p>Oracle: renaming terms that occur in exactly one document must not change any classifier
 * output for texts that do not contain them. The "extra" document below has no class field, so it
 * is not a training document; it only contributes two single-occurrence terms to the text field.
 * The control extra document uses two CJK ideographs below the surrogate range (orders agree); the
 * trigger extra document uses an emoji and a fullwidth letter. Everything else is identical.
 *
 * <p>testFailOpen* assert the correct (control) result and fail on affected builds. testLimit*
 * record reach and cost and pass. OBS lines on stdout carry the observations.
 */
public class TestPerceptronFstOrderSecurityReach extends LuceneTestCase {

  private static final String TEXT = "text";
  private static final String CLASS = "cls";

  /** U+1F600 GRINNING FACE (UTF-8 F0 9F 98 80) and U+FF41 FULLWIDTH LATIN SMALL LETTER A (EF BD 81). */
  private static final String TRIGGER_EXTRA =
      new String(Character.toChars(0x1F600)) + " " + "ａ";

  /** U+4E00 and U+4E01 (UTF-8 E4 B8 80 / E4 B8 81): UTF-16 and UTF-8 orders agree. */
  private static final String CONTROL_EXTRA = "一 丁";

  /**
   * Dense ASCII corpus: one word per initial letter a..z, so that the FST root has enough arcs to
   * be written with direct addressing (the encoding where the bug's effect is widest).
   */
  private static final String[] POS = {
    "apple bonus cash", "deal easy free", "gift huge income", "jackpot kudos lucky money"
  };

  private static String[] neg(String extraToken) {
    return new String[] {
      "notes office project " + extraToken, "quarterly report schedule", "team update vendor",
      "weekly xerox yearly zone"
    };
  }

  private static final String[] SPARSE_POS = {
    "free prize cash win", "free cash offer win", "prize win bonus free"
  };

  private static String[] sparseNeg(String numberToken) {
    return new String[] {
      "meeting agenda report " + numberToken, "report notes agenda", "lunch meeting notes team"
    };
  }

  private static final String[] PROBES = {
    "apple bonus cash", "free", "money", "lucky", "jackpot", "notes office project", "report", "zone"
  };

  private static final String[] SPARSE_PROBES = {
    "free prize cash win", "free", "win", "prize", "cash", "meeting agenda report", "meeting", "notes"
  };

  private static void addDoc(IndexWriter w, FieldType ft, String text, Boolean c)
      throws IOException {
    Document doc = new Document();
    doc.add(new Field(TEXT, text, ft));
    if (c != null) {
      doc.add(new StringField(CLASS, Boolean.toString(c), Field.Store.YES));
    }
    w.addDocument(doc);
  }

  /** Result of training plus classifying the probes; or the Throwable thrown. */
  private static final class Run {
    final List<String> outputs = new ArrayList<>();
    Throwable thrown;
    double bias;
    long trainMillis;

    String describe() {
      return thrown != null
          ? "THREW " + top(thrown)
          : outputs.toString();
    }
  }

  private static String top(Throwable t) {
    StringBuilder sb = new StringBuilder(t.toString());
    StackTraceElement[] st = t.getStackTrace();
    for (int i = 0; i < Math.min(12, st.length); i++) sb.append(" <- ").append(st[i]);
    return sb.toString();
  }

  private static String fmt(ClassificationResult<Boolean> r, double bias) {
    double s = r.score();
    String out;
    if (s >= 1.0) {
      out = "far";
    } else {
      double d = -bias * Math.log(1 - s);
      out = Long.toString(Math.round(r.assignedClass() ? bias + d : bias - d));
    }
    return r.assignedClass() + "/" + out;
  }

  private Run train(String[] pos, String[] neg, String extra, String[] probes) throws IOException {
    return train(pos, neg, extra, probes, false);
  }

  private Run train(String[] pos, String[] neg, String extra, String[] probes, boolean deleteExtra)
      throws IOException {
    Run run = new Run();
    Analyzer analyzer = new MockAnalyzer(random(), MockTokenizer.WHITESPACE, false);
    try (Directory dir = newDirectory()) {
      IndexWriterConfig iwc = new IndexWriterConfig(analyzer);
      iwc.setMergePolicy(NoMergePolicy.INSTANCE);
      try (IndexWriter w = new IndexWriter(dir, iwc)) {
        FieldType ft = new FieldType(TextField.TYPE_STORED);
        ft.setStoreTermVectors(true);
        for (String p : pos) addDoc(w, ft, p, true);
        for (String n : neg) addDoc(w, ft, n, false);
        if (extra != null) {
          // not a training doc: no class field
          Document doc = new Document();
          doc.add(new Field(TEXT, extra, ft));
          doc.add(new StringField("id", "extra", Field.Store.NO));
          w.addDocument(doc);
        }
        if (deleteExtra) {
          w.commit();
          w.deleteDocuments(new Term("id", "extra"));
        }
      }
      try (IndexReader reader = DirectoryReader.open(dir)) {
        run.bias = (double) reader.getSumTotalTermFreq(TEXT) / reader.getDocCount(TEXT);
        long t0 = System.nanoTime();
        try {
          BooleanPerceptronClassifier c =
              new BooleanPerceptronClassifier(reader, analyzer, null, 1, null, CLASS, TEXT);
          run.trainMillis = (System.nanoTime() - t0) / 1_000_000;
          for (String p : probes) {
            run.outputs.add(p + "=" + fmt(c.assignClass(p), run.bias));
          }
        } catch (Throwable t) {
          run.trainMillis = (System.nanoTime() - t0) / 1_000_000;
          run.thrown = t;
        }
      }
    } finally {
      analyzer.close();
    }
    return run;
  }

  /**
   * One document that is not even in the training set, holding an emoji token and a fullwidth
   * token, changes the classification of unrelated ASCII texts. Smallest first byte here is '0'.
   */
  public void testFailOpenOneUnlabeledDocChangesUnrelatedClassifications() throws IOException {
    String[] neg = neg("0900");
    Run control = train(POS, neg, CONTROL_EXTRA, PROBES);
    Run trigger = train(POS, neg, TRIGGER_EXTRA, PROBES);
    System.out.println("OBS one-unlabeled-doc bias=" + control.bias + " control=" + control.describe());
    System.out.println("OBS one-unlabeled-doc bias=" + trigger.bias + " trigger=" + trigger.describe());
    assertNull("control must train", control.thrown);
    assertEquals(
        "classifier outputs for ASCII texts must not depend on the spelling of an unrelated"
            + " single-occurrence document; trigger="
            + trigger.describe(),
        control.outputs,
        trigger.outputs);
  }

  /**
   * The application deletes the trigger document before training (no merge has run yet). The
   * classifier reads the text field's terms dictionary, which still holds the deleted document's
   * terms, so the result must still equal the control.
   */
  public void testFailOpenDeletedTriggerDocStillChangesClassifications() throws IOException {
    String[] neg = neg("0900");
    Run control = train(POS, neg, CONTROL_EXTRA, PROBES, true);
    Run trigger = train(POS, neg, TRIGGER_EXTRA, PROBES, true);
    System.out.println("OBS deleted-extra-doc control=" + control.describe());
    System.out.println("OBS deleted-extra-doc trigger=" + trigger.describe());
    assertNull("control must train", control.thrown);
    assertEquals(
        "a deleted, unlabeled document must not change classifier outputs; trigger="
            + trigger.describe(),
        control.outputs,
        trigger.outputs);
  }

  /**
   * Records which smallest first byte, and which root density, lets the trigger change unrelated
   * ASCII outputs. Hypothesis from reading FSTCompiler.writePresenceBits: with a direct-addressing
   * root whose arcs end ...F0, EF, the presence bitset is one byte longer than the reader expects
   * exactly when (0xF0 - smallestFirstByte) % 8 == 0. The sparse corpus (about 15 root arcs) is
   * expected to use binary search at the root instead, where ASCII lookups still work. Asserts the
   * hypothesis for the dense corpus and "no change" for the sparse corpus.
   */
  public void testLimitWhichCorporaCollapse() throws IOException {
    assumeFalse("needs assertions disabled (with -ea the constructor throws)", TEST_ASSERTS_ENABLED);
    String[] tokens = {"0900", "1900", "8am", "@home", "Hotel", "Phone", "aa"};
    for (boolean dense : new boolean[] {true, false}) {
      for (String tok : tokens) {
        String[] pos = dense ? POS : SPARSE_POS;
        String[] neg = dense ? neg(tok) : sparseNeg(tok);
        String[] probes = dense ? PROBES : SPARSE_PROBES;
        Run control = train(pos, neg, CONTROL_EXTRA, probes);
        Run trigger = train(pos, neg, TRIGGER_EXTRA, probes);
        Set<Character> firsts = new java.util.TreeSet<>();
        for (String d : pos) for (String t : d.split(" ")) firsts.add(t.charAt(0));
        for (String d : neg) for (String t : d.split(" ")) firsts.add(t.charAt(0));
        int minFirst = firsts.iterator().next();
        boolean predicted = dense && (0xF0 - minFirst) % 8 == 0;
        boolean same =
            control.thrown == null && trigger.thrown == null && control.outputs.equals(trigger.outputs);
        System.out.println(
            String.format(
                Locale.ROOT,
                "OBS corpus=%s token=%s asciiRootArcs=%d smallestFirstByte=0x%02X predictedChange=%s outputsEqual=%s control=%s trigger=%s",
                dense ? "dense" : "sparse",
                tok,
                firsts.size(),
                minFirst,
                predicted,
                same,
                control.describe(),
                trigger.describe()));
        assertNull(control.thrown);
        assertEquals("corpus dense=" + dense + " token " + tok + ": " + trigger.describe(), predicted, !same);
      }
    }
  }

  /**
   * Cost and failure modes on larger random corpora (fixed seeds, java.util.Random). Records
   * training time, exceptions, and how many single-word ASCII probes change class. Asserts only a
   * time bound.
   */
  public void testLimitRandomCorporaCostAndFailureModes() throws IOException {
    assumeFalse("needs assertions disabled (with -ea the constructor throws)", TEST_ASSERTS_ENABLED);
    long maxMillis = 0;
    int threw = 0, changed = 0, corpora = 12;
    for (int seed = 1; seed <= corpora; seed++) {
      Random r = new Random(seed);
      List<String> words = new ArrayList<>();
      for (int i = 0; i < 400; i++) {
        StringBuilder sb = new StringBuilder();
        int len = 1 + r.nextInt(7);
        for (int j = 0; j < len; j++) {
          sb.append(r.nextInt(8) == 0 ? (char) ('0' + r.nextInt(10)) : (char) ('a' + r.nextInt(26)));
        }
        words.add(sb.toString());
      }
      String[] pos = new String[150], neg = new String[150];
      for (int d = 0; d < 150; d++) {
        StringBuilder p = new StringBuilder(), n = new StringBuilder();
        for (int k = 0; k < 8; k++) {
          p.append(words.get(r.nextInt(200))).append(' ');
          n.append(words.get(200 + r.nextInt(200))).append(' ');
        }
        pos[d] = p.toString().trim();
        neg[d] = n.toString().trim();
      }
      Set<String> probeSet = new LinkedHashSet<>(words.subList(0, 20));
      probeSet.addAll(words.subList(200, 220));
      String[] probes = probeSet.toArray(new String[0]);
      Run control = train(pos, neg, CONTROL_EXTRA, probes);
      Run trigger = train(pos, neg, TRIGGER_EXTRA, probes);
      maxMillis = Math.max(maxMillis, Math.max(control.trainMillis, trigger.trainMillis));
      int diff = 0, outDiff = 0, zero = 0, controlTrue = 0;
      if (trigger.thrown != null) {
        threw++;
      } else {
        for (int i = 0; i < probes.length; i++) {
          String a = control.outputs.get(i), b = trigger.outputs.get(i);
          String ca = a.substring(a.indexOf('=') + 1, a.indexOf('/'));
          String cb = b.substring(b.indexOf('=') + 1, b.indexOf('/'));
          if (ca.equals("true")) controlTrue++;
          if (!ca.equals(cb)) diff++;
          if (!a.equals(b)) outDiff++;
          if (b.endsWith("/0")) zero++;
        }
        if (diff > 0) changed++;
      }
      System.out.println(
          String.format(
              Locale.ROOT,
              "OBS random-corpus seed=%d controlThrew=%s triggerThrew=%s probesWithChangedClass=%d/%d (controlTrue=%d) probesWithChangedOutput=%d triggerOutputZero=%d trainMs=%d/%d",
              seed,
              control.thrown == null ? "no" : top(control.thrown),
              trigger.thrown == null ? "no" : top(trigger.thrown),
              diff,
              probes.length,
              controlTrue,
              outDiff,
              zero,
              control.trainMillis,
              trigger.trainMillis));
      assertNull(control.thrown);
    }
    System.out.println(
        "OBS random-corpora corpora=" + corpora + " triggerThrew=" + threw
            + " triggerChangedSomeClass=" + changed + " maxTrainMs=" + maxMillis);
    assertTrue("training took " + maxMillis + " ms", maxMillis < 120_000);
  }
}
