import static org.junit.Assert.assertEquals;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.index.memory.MemoryIndex;
import org.apache.lucene.queryparser.complexPhrase.ComplexPhraseQueryParser;
import org.apache.lucene.search.Query;
import org.junit.Test;

public class TestComplexPhraseQuotedStopWordOverMemoryIndex {

  @Test
  public void testQuotedStopWord() throws Exception {
    Analyzer analyzer = new EnglishAnalyzer();
    MemoryIndex index = new MemoryIndex();
    index.addField("f", "the quick brown fox", analyzer);
    Query query = new ComplexPhraseQueryParser("f", analyzer).parse("\"the\"");
    // expected 0 hits: the analyzer removes the phrase's only word; actual: NullPointerException
    assertEquals(0, index.createSearcher().count(query));
  }
}
