/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.analysis.miscellaneous;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.CharArraySet;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.StopFilter;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.WhitespaceTokenizer;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReader;
import org.apache.lucene.index.PostingsEnum;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.DocIdSetIterator;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.PhraseQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.tests.util.LuceneTestCase;

/**
 * Independent check: a ConditionalTokenFilter whose wrapped filter does not touch positions must
 * report the same end-of-stream position increment as its input (TokenStream#end: skipped positions
 * are applied to the final position increment).
 *
 * <p>Chain: WhitespaceTokenizer -> StopFilter({"the"}) -> [ConditionalTokenFilter(wrapped)].
 */
public class TestConditionalFinalPosInc extends LuceneTestCase {

  private static final CharArraySet STOP = new CharArraySet(List.of("the"), false);

  /** A filter that changes nothing. */
  static final class PassThrough extends TokenFilter {
    PassThrough(TokenStream in) {
      super(in);
    }

    @Override
    public boolean incrementToken() throws IOException {
      return input.incrementToken();
    }
  }

  /** Consumes the stream; returns terms followed by "END:posInc=<n>,offset=<o>". */
  static List<String> consume(TokenStream ts) throws IOException {
    List<String> out = new ArrayList<>();
    CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
    PositionIncrementAttribute posInc = ts.addAttribute(PositionIncrementAttribute.class);
    OffsetAttribute off = ts.addAttribute(OffsetAttribute.class);
    ts.reset();
    while (ts.incrementToken()) {
      out.add(term.toString() + "/" + posInc.getPositionIncrement());
    }
    ts.end();
    out.add("END:posInc=" + posInc.getPositionIncrement() + ",offset=" + off.endOffset());
    ts.close();
    return out;
  }

  static TokenStream stopChain(String text) {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new java.io.StringReader(text));
    return new StopFilter(tok, STOP);
  }

  static TokenStream conditional(
      TokenStream in, boolean filterEverything, Function<TokenStream, TokenStream> wrapped) {
    return new ConditionalTokenFilter(in, wrapped) {
      @Override
      protected boolean shouldFilter() {
        return filterEverything;
      }
    };
  }

  // ---- control: the plain chain has final posInc 1 for "abc the" ----
  public void testControlPlainChain() throws IOException {
    assertEquals(List.of("abc/1", "END:posInc=1,offset=7"), consume(stopChain("abc the")));
    assertEquals(
        List.of("abc/1", "END:posInc=1,offset=7"),
        consume(new PassThrough(stopChain("abc the"))));
  }

  // ---- control: condition false for the last token -> hole kept ----
  public void testControlConditionNeverTrue() throws IOException {
    assertEquals(
        List.of("abc/1", "END:posInc=1,offset=7"),
        consume(conditional(stopChain("abc the"), false, PassThrough::new)));
  }

  // ---- control: the wrapped filter's OWN trailing hole is kept ----
  public void testControlHoleCreatedInsideDelegate() throws IOException {
    Tokenizer tok = new WhitespaceTokenizer();
    tok.setReader(new java.io.StringReader("abc the"));
    TokenStream ts = conditional(tok, true, in -> new StopFilter(in, STOP));
    assertEquals(List.of("abc/1", "END:posInc=1,offset=7"), consume(ts));
  }

  // ---- claim: condition true for the last token, pass-through wrapped filter -> hole lost ----
  public void testPassThroughDelegateKeepsUpstreamHole() throws IOException {
    assertEquals(
        List.of("abc/1", "END:posInc=1,offset=7"),
        consume(conditional(stopChain("abc the"), true, PassThrough::new)));
  }

  public void testProtectedTermFilterLowerCase() throws IOException {
    CharArraySet prot = new CharArraySet(List.of("x"), false);
    // last token protected: hole kept
    assertEquals(
        List.of("x/1", "END:posInc=1,offset=5"),
        consume(new ProtectedTermFilter(prot, stopChain("x the"), LowerCaseFilter::new)));
    // last token not protected (goes through LowerCaseFilter): hole should still be kept
    assertEquals(
        List.of("abc/1", "END:posInc=1,offset=7"),
        consume(new ProtectedTermFilter(prot, stopChain("ABC the"), LowerCaseFilter::new)));
  }

  public void testCustomAnalyzerWhenTerm() throws IOException {
    Analyzer plain =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("stop")
            .addTokenFilter("lowercase")
            .build();
    Analyzer cond =
        CustomAnalyzer.builder()
            .withTokenizer("whitespace")
            .addTokenFilter("stop")
            .whenTerm(t -> true)
            .addTokenFilter("lowercase")
            .endwhen()
            .build();
    List<String> expected = consume(plain.tokenStream("f", "ABC the"));
    assertEquals(List.of("abc/1", "END:posInc=1,offset=7"), expected);
    assertEquals(expected, consume(cond.tokenStream("f", "ABC the")));
    plain.close();
    cond.close();
  }

  /** Consumer-level effect: TokenStreamToAutomaton encodes trailing holes from end(). */
  public void testTokenStreamToAutomatonTrailingHole() throws IOException {
    List<String> plain = finiteStrings(new PassThrough(stopChain("abc the")));
    List<String> cond = finiteStrings(conditional(stopChain("abc the"), true, PassThrough::new));
    // plain: "abc" then POS_SEP (0x1f) then HOLE (0x1e)
    assertEquals(List.of("abc\\u001f\\u001e"), plain);
    assertEquals("conditional vs plain automaton strings", plain, cond);
  }

  private static List<String> finiteStrings(TokenStream ts) throws IOException {
    org.apache.lucene.util.automaton.Automaton a =
        new org.apache.lucene.analysis.TokenStreamToAutomaton().toAutomaton(ts);
    ts.close();
    List<String> out = new ArrayList<>();
    org.apache.lucene.util.automaton.FiniteStringsIterator it =
        new org.apache.lucene.util.automaton.FiniteStringsIterator(a);
    for (org.apache.lucene.util.IntsRef r = it.next(); r != null; r = it.next()) {
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < r.length; i++) {
        int c = r.ints[r.offset + i];
        if (c < 0x20) {
          sb.append(String.format(java.util.Locale.ROOT, "\\u%04x", c));
        } else {
          sb.append((char) c);
        }
      }
      out.add(sb.toString());
    }
    return out;
  }

  /** Index-level effect: multi-valued field, default position increment gap (0). */
  public void testIndexedPositionsAndPhraseMatch() throws IOException {
    int[] plain = indexAndCheck(false);
    int[] cond = indexAndCheck(true);
    // plain: abc=0, end +1 (hole) -> def=2 ; phrase "abc def" does not match
    assertEquals("plain chain: [posAbc, posDef, phraseHits]", 0, plain[0]);
    assertEquals(2, plain[1]);
    assertEquals(0, plain[2]);
    assertArrayEquals(
        "conditional chain with a pass-through wrapped filter must index like the plain chain;"
            + " plain [posAbc,posDef,phraseHits]="
            + java.util.Arrays.toString(plain)
            + " conditional="
            + java.util.Arrays.toString(cond),
        plain,
        cond);
  }

  private int[] indexAndCheck(boolean useConditional) throws IOException {
    Analyzer a =
        new Analyzer() {
          @Override
          protected TokenStreamComponents createComponents(String fieldName) {
            Tokenizer tok = new WhitespaceTokenizer();
            TokenStream ts = new StopFilter(tok, STOP);
            ts = useConditional ? conditional(ts, true, PassThrough::new) : new PassThrough(ts);
            return new TokenStreamComponents(tok, ts);
          }
        };
    try (Directory dir = newDirectory();
        IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(a))) {
      Document doc = new Document();
      doc.add(new Field("f", "abc the", TextField.TYPE_NOT_STORED));
      doc.add(new Field("f", "def", TextField.TYPE_NOT_STORED));
      w.addDocument(doc);
      w.commit();
      try (DirectoryReader r = DirectoryReader.open(w)) {
        LeafReader leaf = r.leaves().get(0).reader();
        int[] res = new int[3];
        res[0] = firstPos(leaf, "abc");
        res[1] = firstPos(leaf, "def");
        PhraseQuery pq = new PhraseQuery("f", "abc", "def");
        res[2] = new IndexSearcher(r).count(pq);
        return res;
      }
    } finally {
      a.close();
    }
  }

  private static int firstPos(LeafReader leaf, String t) throws IOException {
    PostingsEnum pe = leaf.postings(new Term("f", t), PostingsEnum.POSITIONS);
    assertNotNull(pe);
    assertTrue(pe.nextDoc() != DocIdSetIterator.NO_MORE_DOCS);
    return pe.nextPosition();
  }
}
