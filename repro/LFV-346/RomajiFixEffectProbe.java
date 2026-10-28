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
package org.apache.lucene.analysis.ja;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.QueryVisitor;
import org.apache.lucene.search.suggest.InputIterator;
import org.apache.lucene.search.suggest.Lookup;
import org.apache.lucene.search.suggest.analyzing.AnalyzingSuggester;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.QueryBuilder;

/**
 * Prints what JapaneseCompletionAnalyzer produces for short and long all-kana inputs, and what
 * QueryBuilder and AnalyzingSuggester do with them. Run it on stock Lucene and on a fixed
 * KatakanaRomanizer to compare. No assertions: every outcome, including an Error, is printed.
 */
public class RomajiFixEffectProbe {

  static String stack(Analyzer a, String s) {
    long t0 = System.nanoTime();
    try (TokenStream ts = a.tokenStream("f", s)) {
      CharTermAttribute term = ts.addAttribute(CharTermAttribute.class);
      PositionIncrementAttribute p = ts.addAttribute(PositionIncrementAttribute.class);
      ts.reset();
      long tokens = 0;
      int positions = 0;
      int maxStack = 0;
      int stack = 0;
      Set<String> distinct = new HashSet<>();
      Set<String> maxDistinct = new HashSet<>();
      while (ts.incrementToken()) {
        tokens++;
        if (p.getPositionIncrement() == 0) {
          stack++;
          distinct.add(term.toString());
        } else {
          positions++;
          stack = 0;
          distinct = new HashSet<>();
        }
        if (stack > maxStack) {
          maxStack = stack;
          maxDistinct = distinct;
        }
      }
      ts.end();
      return "positions="
          + positions
          + " tokens="
          + tokens
          + " maxRomanizedAtOnePosition="
          + maxStack
          + " distinct="
          + maxDistinct.size()
          + " ms="
          + (System.nanoTime() - t0) / 1_000_000;
    } catch (Throwable t) {
      return "threw " + t + " after " + (System.nanoTime() - t0) / 1_000_000 + " ms";
    }
  }

  static int countTerms(Query q) {
    int[] n = new int[1];
    q.visit(
        new QueryVisitor() {
          @Override
          public void consumeTerms(Query query, org.apache.lucene.index.Term... terms) {
            n[0] += terms.length;
          }

          @Override
          public QueryVisitor getSubVisitor(
              org.apache.lucene.search.BooleanClause.Occur occur, Query parent) {
            return this;
          }
        });
    return n[0];
  }

  public static void main(String[] args) throws Exception {
    String[] inputs = {
      "しんじつはいつもひとつ",
      "ちいさいしんかんせんにのってしずおかにいきました",
      "しゅっちょうさきでしんかんせんのじかんをしらべる",
      "ジャンジャンバリバリチャンチャンシャンシャン",
      "わたしはしんぶんしゃのしんじんきしゃでまいにちしごとをしています",
      "ン".repeat(32),
      "し".repeat(32),
      "わたしはしんぶんしゃのしんじんきしゃでまいにちしごとをしています".repeat(2),
    };
    System.out.println("max heap MB=" + (Runtime.getRuntime().maxMemory() >> 20));
    try (Analyzer index = new JapaneseCompletionAnalyzer();
        Analyzer query = new JapaneseCompletionAnalyzer(null, JapaneseCompletionFilter.Mode.QUERY)) {
      for (String s : inputs) {
        System.out.println("INDEX len=" + s.length() + " " + stack(index, s) + " : " + s);
        System.out.println("QUERY len=" + s.length() + " " + stack(query, s) + " : " + s);
      }

      QueryBuilder qb = new QueryBuilder(query);
      for (String s : new String[] {"しんぶんしゃ", inputs[4], "し".repeat(32)}) {
        long t0 = System.nanoTime();
        String r;
        try {
          Query q = qb.createBooleanQuery("title", s);
          r = q.getClass().getSimpleName() + "(" + countTerms(q) + " terms)";
        } catch (Throwable t) {
          r = "threw " + t.getClass().getSimpleName();
        }
        System.out.println(
            "QueryBuilder(QUERY, maxClauseCount="
                + IndexSearcher.getMaxClauseCount()
                + ") len="
                + s.length()
                + " -> "
                + r
                + " in "
                + (System.nanoTime() - t0) / 1_000_000
                + " ms");
      }

      AnalyzingSuggester sugg = new AnalyzingSuggester(new ByteBuffersDirectory(), "jcf", index, query);
      String[] keys = {"新宿駅", "東京タワー", "新聞社"};
      sugg.build(iterator(keys));
      lookups(sugg, new String[] {"しん", "しんぶ", "とうきょう", inputs[3], "し".repeat(32)});

      // a long suggestion: does a long prefix of its reading still find it?
      String longKey = inputs[3];
      long t0 = System.nanoTime();
      try {
        AnalyzingSuggester sugg2 =
            new AnalyzingSuggester(new ByteBuffersDirectory(), "jcf2", index, query);
        sugg2.build(iterator(new String[] {longKey, "新幹線"}));
        System.out.println(
            "AnalyzingSuggester.build(long key len=" + longKey.length() + ") ok in "
                + (System.nanoTime() - t0) / 1_000_000 + " ms");
        lookups(
            sugg2,
            new String[] {
              "しんかん",
              longKey.substring(0, 6),
              longKey.substring(0, 10),
              longKey.substring(0, 16),
              longKey.substring(0, 19),
              longKey
            });
      } catch (Throwable t) {
        System.out.println(
            "AnalyzingSuggester.build(long key len=" + longKey.length() + ") threw "
                + t.getClass().getSimpleName() + " after " + (System.nanoTime() - t0) / 1_000_000 + " ms");
      }
    }
  }

  static void lookups(AnalyzingSuggester sugg, String[] queries) {
      for (String s : queries) {
        long t0 = System.nanoTime();
        String r;
        try {
          List<Lookup.LookupResult> res = sugg.lookup(s, false, 5);
          r = res.toString();
        } catch (Throwable t) {
          r = "threw " + t.getClass().getSimpleName();
        }
        System.out.println(
            "AnalyzingSuggester.lookup len="
                + s.length()
                + " -> "
                + r
                + " in "
                + (System.nanoTime() - t0) / 1_000_000
                + " ms : "
                + s);
      }
  }

  static InputIterator iterator(String[] keys) {
    return new InputIterator() {
      int i = -1;

      @Override
      public BytesRef next() {
        return ++i < keys.length ? new BytesRef(keys[i]) : null;
      }

      @Override
      public long weight() {
        return 10 - i;
      }

      @Override
      public BytesRef payload() {
        return null;
      }

      @Override
      public boolean hasPayloads() {
        return false;
      }

      @Override
      public Set<BytesRef> contexts() {
        return null;
      }

      @Override
      public boolean hasContexts() {
        return false;
      }
    };
  }
}
