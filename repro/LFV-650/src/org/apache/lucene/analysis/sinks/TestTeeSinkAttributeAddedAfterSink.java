package org.apache.lucene.analysis.sinks;

import java.io.StringReader;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.en.PorterStemFilter;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.tests.util.LuceneTestCase;

public class TestTeeSinkAttributeAddedAfterSink extends LuceneTestCase {

  public void testSinkAfterStemmerOnTee() throws Exception {
    Tokenizer tokenizer = new WhitespaceTokenizer();
    tokenizer.setReader(new StringReader("foxes"));
    TeeSinkTokenFilter tee = new TeeSinkTokenFilter(tokenizer);
    // sink first, then a filter on the tee, as in the class javadoc
    TokenStream sink = tee.newSinkTokenStream();
    TokenStream stemmed = new PorterStemFilter(tee);
    stemmed.reset();
    while (stemmed.incrementToken()) {}
    stemmed.end();
    stemmed.close();

    CharTermAttribute term = sink.getAttribute(CharTermAttribute.class);
    sink.reset();
    try {
      assertTrue(sink.incrementToken());
    } catch (IllegalArgumentException e) {
      throw new AssertionError("expected the sink to replay \"foxes\", but it threw " + e, e);
    }
    assertEquals("foxes", term.toString());
  }
}
