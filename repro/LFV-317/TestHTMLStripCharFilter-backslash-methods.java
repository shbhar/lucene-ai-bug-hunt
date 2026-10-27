// Paste into lucene/analysis/common/src/test/org/apache/lucene/analysis/charfilter/TestHTMLStripCharFilter.java
// (for example after testSpaceAfterEquals); assertHTMLStripsTo is that class's existing helper.

  // A backslash has no special meaning in an HTML attribute value: a quoted value ends at the
  // next matching quote, even when the last character before it is a backslash.

  public void testBackslashAtEndOfDoubleQuotedAttributeValue() throws Exception {
    assertHTMLStripsTo(
        "<a href=\"C:\\Temp\\\">folder</a> text after <a href=\"x\">y</a> end",
        "folder text after y end",
        null);
  }

  public void testBackslashAtEndOfSingleQuotedAttributeValue() throws Exception {
    assertHTMLStripsTo(
        "<a title='C:\\Temp\\'>folder</a> text after <a title='x'>y</a> end",
        "folder text after y end",
        null);
  }

  public void testBackslashAtEndOfEventAttributeValue() throws Exception {
    assertHTMLStripsTo(
        "<a onclick=\"C:\\Temp\\\">folder</a> text after <a onclick=\"x\">y</a> end",
        "folder text after y end",
        null);
  }

  public void testBackslashElsewhereInAttributeValue() throws Exception {
    // no backslash right before the closing quote
    assertHTMLStripsTo(
        "<a href=\"C:\\Temp\">folder</a> text after <a href=\"x\">y</a> end",
        "folder text after y end",
        null);
    // the value ends in a backslash, but a later attribute closes the tag
    assertHTMLStripsTo(
        "<a href=\"C:\\Temp\\\" class=\"c\">folder</a> text after <a href=\"x\">y</a> end",
        "folder text after y end",
        null);
  }
