package org.example.queryparser;

import static org.junit.Assert.assertEquals;

import java.util.List;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryparser.flexible.standard.StandardQueryParser;
import org.junit.Test;

public class TestStandardQueryParserPositionIncrementsDefault {

  // Javadoc of setEnablePositionIncrements: "Default: false."; expected f:"quick fox" (the query with the setter at false), actual f:"quick ? fox".
  @Test
  public void testUntouchedParserBuildsTheQueryOfTheDocumentedDefault() throws Exception {
    StandardAnalyzer analyzer = new StandardAnalyzer(new CharArraySet(List.of("the"), true));
    StandardQueryParser documentedDefault = new StandardQueryParser(analyzer);
    documentedDefault.setEnablePositionIncrements(false);
    StandardQueryParser untouched = new StandardQueryParser(analyzer);
    assertEquals(
        documentedDefault.parse("\"quick the fox\"", "f"), untouched.parse("\"quick the fox\"", "f"));
  }
}
