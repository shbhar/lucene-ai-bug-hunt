/*
 * Licensed to the Apache Software Foundation (ASF) under one or more contributor license agreements. See the NOTICE
 * file distributed with this work for additional information regarding copyright ownership. The ASF licenses this
 * file to You under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.lucene.analysis.br;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordTokenizer;
import org.apache.lucene.tests.analysis.BaseTokenStreamTestCase;

/**
 * BrazilianStemmer step 1 has the rule "logias" (in R2) -> replace with "log", next to the singular rule "logia" ->
 * "log". The plural rule calls replaceSuffix(CT, "logias", "log") but drops the returned string and still reports
 * that a suffix was removed, so plural forms keep the whole suffix and no longer conflate with the singular.
 */
public class TestBrazilianLogiasPlural extends BaseTokenStreamTestCase {

  private static Analyzer stemOnly() {
    return new Analyzer() {
      @Override
      protected TokenStreamComponents createComponents(String fieldName) {
        Tokenizer t = new KeywordTokenizer();
        return new TokenStreamComponents(t, new BrazilianStemFilter(t));
      }
    };
  }

  public void testSingularControl() throws Exception {
    try (Analyzer a = stemOnly()) {
      checkOneTerm(a, "metodologia", "metodolog");
      checkOneTerm(a, "antropologia", "antropolog");
    }
  }

  public void testPluralLogiasIsReplacedByLog() throws Exception {
    try (Analyzer a = stemOnly()) {
      checkOneTerm(a, "metodologias", "metodolog");
      checkOneTerm(a, "antropologias", "antropolog");
      checkOneTerm(a, "terminologias", "terminolog");
    }
  }

  public void testBrazilianAnalyzerConflatesSingularAndPlural() throws Exception {
    try (Analyzer a = new BrazilianAnalyzer()) {
      assertAnalyzesTo(a, "metodologia metodologias", new String[] {"metodolog", "metodolog"});
    }
  }
}
