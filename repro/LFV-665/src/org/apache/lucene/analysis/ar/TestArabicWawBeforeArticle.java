/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this
 * file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.analysis.ar;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * ArabicAnalyzer implements light10 (Larkey, Ballesteros and Connell, "Light Stemming for Arabic Information
 * Retrieval", section 2.2): step 1 removes an initial waw if 3 or more characters remain, step 2 then removes a
 * definite article if 2 or more characters remain, step 3 removes suffixes. ArabicStemmer.stemPrefix removes at most
 * one prefix, so after it removes the waw it never removes the article that follows (bal-, kal-, fal-, lil-).
 */
public class TestArabicWawBeforeArticle extends BaseTokenStreamTestCase {

  private static Analyzer stemOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new KeywordTokenizer();
        return new TokenStreamComponents(t, new ArabicStemFilter(t));
      }
    };
  }

  public void testArticleAloneControl() throws Exception {
    try (Analyzer a = stemOnly()) {
      checkOneTerm(a, "بالكتاب", "كتاب"); // bi-al-kitab, "with the book"
      checkOneTerm(a, "للطلاب", "طلاب"); // li-l-tullab, "for the students"
      checkOneTerm(a, "والكتاب", "كتاب"); // wa-al- is in the article list itself
    }
  }

  public void testWawThenArticle() throws Exception {
    try (Analyzer a = stemOnly()) {
      checkOneTerm(a, "وبالكتاب", "كتاب"); // wa-bi-al-kitab, "and with the book"
      checkOneTerm(a, "وللطلاب", "طلاب"); // wa-li-l-tullab, "and for the students"
    }
  }

  public void testArabicAnalyzerConflates() throws Exception {
    try (Analyzer a = new ArabicAnalyzer()) {
      assertAnalyzesTo(a, "الكتاب بالكتاب وبالكتاب", new String[] {"كتاب", "كتاب", "كتاب"});
    }
  }
}
