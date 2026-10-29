package lfv845;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermRangeQuery;

/** Prints how each input parses (classic and flexible); args[0] labels the build. */
public class RangeBoundProbe {
  static String show(String s) {
    if (s == null) return "null";
    return s.replace("\t", "<TAB>").replace("\n", "<LF>").replace("\r", "<CR>").replace("\u3000", "<U+3000>");
  }

  static String describe(Query q) {
    if (q instanceof TermRangeQuery t) {
      return "range lower='" + (t.getLowerTerm() == null ? "null" : show(t.getLowerTerm().utf8ToString()))
          + "' upper='" + (t.getUpperTerm() == null ? "null" : show(t.getUpperTerm().utf8ToString())) + "'";
    }
    return q.getClass().getSimpleName() + " " + show(q.toString());
  }

  public static void main(String[] args) throws Exception {
    String label = args.length > 0 ? args[0] : "?";
    StandardAnalyzer a = new StandardAnalyzer();
    String[] inputs = {
      "date:[20200101 TO 20201231]",
      "date:[20200101\tTO\t20201231]",
      "date:[\n20200101 TO 20201231\n]",
      "date:{20200101 TO 20201231\t}",
      "date:[\u300020200101 TO 20201231]",
      "date:[\r\n  20200101 TO 20201231 \r\n]",
      "date:[\n* TO 20201231]",
      "date:[\"20200101\"\tTO\t\"20201231\"]",
      "k:[a\tb TO c]",
      "k:[\"a\tb\" TO c]",
    };
    for (String in : inputs) {
      for (String p : new String[] {"classic", "flexible"}) {
        String out;
        try {
          Query q = p.equals("classic")
              ? new QueryParser("body", a).parse(in)
              : new StandardQueryParser(a).parse(in, "body");
          out = "OK " + describe(q);
        } catch (Exception e) {
          out = "FAIL " + e.getClass().getSimpleName();
        }
        System.out.println(label + " " + p + " " + show(in) + " -> " + out);
      }
    }
  }
}
