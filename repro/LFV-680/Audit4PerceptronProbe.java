package org.apache.lucene.classification;

import java.util.*;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;

/**
 * Credibility-audit probe (plain main, StandardAnalyzer, default IndexWriterConfig, no test framework).
 * Trains BooleanPerceptronClassifier on a small spam/ham set with batchSize 1, 3 and 8 and compares each
 * trained model with an in-probe reference that follows the code's own update rule but (a) accumulates
 * updates inside a batch and (b) applies every batch, including the last one. Weights start at
 * totalTermFreq, predictions inside a batch use the weights at the start of the batch.
 */
public class Audit4PerceptronProbe {
  static final String[][] DATA = {
    {"cheap pills buy now", "true"}, {"meeting at noon tomorrow", "false"},
    {"buy cheap watches now", "true"}, {"lunch tomorrow with the team", "false"},
    {"win money now", "true"}, {"project status meeting notes", "false"},
    {"cheap money fast", "true"}, {"team lunch notes", "false"},
    {"buy pills", "true"}, {"status of the project tomorrow", "false"},
    {"meeting notes from the team", "false"}, {"tomorrow lunch at noon", "false"},
  };

  public static void main(String[] args) throws Exception {
    Analyzer analyzer = new StandardAnalyzer();
    Directory dir = new ByteBuffersDirectory();
    FieldType ft = new FieldType(TextField.TYPE_STORED);
    ft.setStoreTermVectors(true);
    ft.freeze();
    try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(analyzer))) {
      for (String[] d : DATA) {
        Document doc = new Document();
        doc.add(new Field("text", d[0], ft));
        doc.add(new StringField("cls", d[1], Field.Store.YES));
        w.addDocument(doc);
      }
      w.forceMerge(1);
    }
    String[] queries = {"cheap pills", "buy now", "money", "meeting tomorrow", "team lunch", "project notes",
        "the meeting", "win cheap", "notes", "buy"};
    double bias = 4.0;
    int totalDiff = 0;
    try (DirectoryReader r = DirectoryReader.open(dir)) {
      for (int bs : new int[] {1, 3, 8}) {
        BooleanPerceptronClassifier c = new BooleanPerceptronClassifier(r, analyzer, null, bs, bias, "cls", "text");
        Map<String, Double> ref = reference(r, analyzer, bs, bias);
        int diff = 0;
        StringBuilder sb = new StringBuilder();
        for (String q : queries) {
          boolean real = c.assignClass(q).assignedClass();
          boolean exp = score(ref, analyzer, q) >= bias;
          if (real != exp) { diff++; sb.append(" '").append(q).append("' real=").append(real).append(" ref=").append(exp); }
        }
        totalDiff += diff;
        System.out.println("batchSize=" + bs + " disagreements=" + diff + "/" + queries.length + sb);
      }
    }
    System.out.println(totalDiff == 0 ? "PROBE: no disagreement" : "PROBE: disagreement found");
  }

  static double score(Map<String, Double> wts, Analyzer a, String text) throws Exception {
    double out = 0;
    for (String t : tokens(a, text)) { Double d = wts.get(t); if (d != null) out += d.longValue(); }
    return out;
  }

  static List<String> tokens(Analyzer a, String text) throws Exception {
    List<String> l = new ArrayList<>();
    try (var ts = a.tokenStream("text", text)) {
      var att = ts.addAttribute(org.apache.lucene.analysis.tokenattributes.CharTermAttribute.class);
      ts.reset();
      while (ts.incrementToken()) l.add(att.toString());
      ts.end();
    }
    return l;
  }

  static Map<String, Double> reference(DirectoryReader r, Analyzer a, int bs, double bias) throws Exception {
    TreeMap<String, Double> pending = new TreeMap<>();
    TermsEnum te = MultiTerms.getTerms(r, "text").iterator();
    BytesRef b;
    while ((b = te.next()) != null) pending.put(b.utf8ToString(), (double) te.totalTermFreq());
    Map<String, Double> model = new HashMap<>(pending);
    int count = 0;
    StoredFields sf = r.storedFields();
    TermVectors tv = r.termVectors();
    for (int doc = 0; doc < r.maxDoc(); doc++) {
      String text = sf.document(doc).get("text");
      boolean correct = Boolean.parseBoolean(sf.document(doc).get("cls"));
      boolean assigned = score(model, a, text) >= bias;
      double mod = Math.signum(Boolean.compare(correct, assigned));
      if (mod != 0) {
        TermsEnum dte = tv.get(doc, "text").iterator();
        while ((b = dte.next()) != null) {
          String t = b.utf8ToString();
          pending.put(t, Math.max(0, pending.get(t) + mod * dte.totalTermFreq()));
        }
      }
      count++;
      if (count % bs == 0) model = new HashMap<>(pending);
    }
    return new HashMap<>(pending);
  }
}
