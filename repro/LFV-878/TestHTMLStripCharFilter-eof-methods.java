  // An unclosed tag-like construct is output as text when the input goes on after the tag name
  // and no '>' closes it (see testMalformedHTML: "<ahref=http:.." strips to "<ahref=http:..").
  // The same must hold when the input ends right after the tag name.

  public void testUnclosedTagNameAtEndOfInput() throws Exception {
    assertHTMLStripsTo("a < b", "a < b", null);
    assertHTMLStripsTo("a<b", "a<b", null);
    assertHTMLStripsTo("x <p", "x <p", null);
    assertHTMLStripsTo("a </b", "a </b", null);
    assertHTMLStripsTo("<ahref", "<ahref", null);
  }

  public void testLastWordAfterUnclosedTagNameIsAnalyzed() throws Exception {
    Analyzer analyzer = newTestAnalyzer();
    assertAnalyzesTo(analyzer, "price a < b c", new String[] {"price", "a", "<", "b", "c"});
    assertAnalyzesTo(analyzer, "price a < b", new String[] {"price", "a", "<", "b"});
    analyzer.close();
  }

  public void testUnclosedTagNameFollowedByMoreInput() throws Exception {
    // control: passes with and without the fix
    assertHTMLStripsTo("a < b ", "a < b ", null);
    assertHTMLStripsTo("a < b c", "a < b c", null);
    assertHTMLStripsTo("a </b ", "a </b ", null);
    assertHTMLStripsTo("<ahref ", "<ahref ", null);
  }
