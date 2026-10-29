package org.apache.lucene.analysis.ar;

import java.io.StringReader;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

public class TestArabicWawThenArticle extends BaseTokenStreamTestCase {

  public void testWawThenArticle() throws Exception {
    Tokenizer tokenizer = new KeywordTokenizer();
    tokenizer.setReader(new StringReader("وبالكتاب"));
    // light10 removes the waw, then the article: expected كتاب, actual بالكتاب
    assertTokenStreamContents(new ArabicStemFilter(tokenizer), new String[] {"كتاب"});
  }
}
