package lfv826probe;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.synonym.SynonymGraphFilter;
import org.apache.lucene.analysis.synonym.SynonymMap;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queries.intervals.IntervalQuery;
import org.apache.lucene.queries.intervals.Intervals;
import org.apache.lucene.queries.intervals.IntervalsSource;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.CharsRef;
import org.apache.lucene.util.CharsRefBuilder;

/**
 * Reach from analyzed text: a query-time synonym york => "new york" (original kept) makes
 * analyzedText("york city", ..., maxGaps -1, ordered) an ordered() over or(york, "new york").
 * Prints the built sources and the hit counts of the direct and the nested form.
 */
public class SynonymReachProbe {
  public static void main(String[] args) throws Exception {
    SynonymMap.Builder b = new SynonymMap.Builder(true);
    b.add(
        new CharsRef("york"),
        SynonymMap.Builder.join(new String[] {"new", "york"}, new CharsRefBuilder()),
        true);
    SynonymMap map = b.build();
    Analyzer syn =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new WhitespaceTokenizer();
            return new TokenStreamComponents(tok, new SynonymGraphFilter(tok, map, true));
          }
        };
    try (ByteBuffersDirectory dir = new ByteBuffersDirectory()) {
      try (IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new WhitespaceAnalyzer()))) {
        Document doc = new Document();
        doc.add(new TextField("f", "in new york city", Field.Store.NO));
        w.addDocument(doc);
      }
      try (DirectoryReader r = DirectoryReader.open(dir)) {
        IndexSearcher s = new IndexSearcher(r);
        IntervalsSource a = Intervals.analyzedText("york city", syn, "f", -1, true);
        System.out.println("SYN analyzedText(\"york city\", maxGaps -1, ordered) = " + a);
        IntervalsSource direct = Intervals.phrase(Intervals.term("in"), a);
        IntervalsSource nested =
            Intervals.phrase(Intervals.term("in"), Intervals.or(a, Intervals.term("boston")));
        IntervalsSource mg =
            Intervals.maxgaps(
                0,
                Intervals.ordered(Intervals.term("in"), Intervals.or(a, Intervals.term("boston"))));
        System.out.println("SYN phrase(in, A) on 'in new york city' = " + s.count(new IntervalQuery("f", direct)));
        System.out.println("SYN phrase(in, or(A, boston)) on 'in new york city' = " + s.count(new IntervalQuery("f", nested)));
        System.out.println("SYN maxgaps(0, ordered(in, or(A, boston))) on 'in new york city' = " + s.count(new IntervalQuery("f", mg)));
      }
    }
  }
}
