package h306hist;

import org.apache.lucene.analysis.miscellaneous.ASCIIFoldingFilter;

/**
 * History repro for Lucene 4.0.0 and later (ASCIIFoldingFilter in analysis-common,
 * org.apache.lucene.analysis.miscellaneous). Uses the public static
 * ASCIIFoldingFilter.foldToASCII(char[], int, char[], int, int), the same table the filter's
 * incrementToken() uses. Exits non-zero (AssertionError) when U+FB05 is left unfolded.
 */
public class LongSTLigatureFold40 {

  static String fold(String in) {
    char[] input = in.toCharArray();
    char[] output = new char[4 * input.length];
    int n = ASCIIFoldingFilter.foldToASCII(input, 0, output, 0, input.length);
    return new String(output, 0, n);
  }

  static String show(String s) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      b.append(c < 0x80 ? String.valueOf(c) : String.format("\\u%04X", (int) c));
    }
    return b.toString();
  }

  static void control(String what, String got, String want) {
    System.out.println("control " + what + " = " + show(got));
    if (!want.equals(got)) {
      throw new IllegalStateException("CONTROL FAILED: " + what + " expected <" + want + "> but was <" + show(got) + ">");
    }
  }

  public static void main(String[] args) {
    control("foldToASCII(fa+U+FB06)", fold("faﬆ"), "fast");
    control("foldToASCII(fa+U+017F+t)", fold("faſt"), "fast");
    String got = fold("faﬅ");
    System.out.println("foldToASCII(fa+U+FB05) = " + show(got));
    if (!"fast".equals(got)) {
      throw new AssertionError("BUG: foldToASCII left U+FB05 unfolded: expected <fast> but was <" + show(got) + ">");
    }
    System.out.println("PASS: U+FB05 folded to st");
  }
}
