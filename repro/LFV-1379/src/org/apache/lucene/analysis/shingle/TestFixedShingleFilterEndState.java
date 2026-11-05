package org.apache.lucene.analysis.shingle;

import java.io.IOException;
import java.io.StringReader;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.tokenattributes.FlagsAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestFixedShingleFilterEndState extends LuceneTestCase {

  public void testEndKeepsInputEndState() throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new StringReader("a b"));
    TokenStream in =
        new TokenFilter(tok) {
          final FlagsAttribute flags = addAttribute(FlagsAttribute.class);

          @Override
          public boolean incrementToken() throws IOException {
            return input.incrementToken();
          }

          @Override
          public void end() throws IOException {
            super.end();
            flags.setFlags(42);
          }
        };
    TokenStream ts = new FixedShingleFilter(in, 2);
    FlagsAttribute flags = ts.addAttribute(FlagsAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {}
    ts.end();
    // expected: the input's end-of-stream flags (42) survive end(); actual on main: 0
    assertEquals(42, flags.getFlags());
    ts.close();
  }
}
