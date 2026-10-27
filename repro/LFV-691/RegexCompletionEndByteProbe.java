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
package repro;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.apache.lucene.analysis.core.WhitespaceAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PostingsFormat;
import org.apache.lucene.codecs.perfield.PerFieldPostingsFormat;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.suggest.document.CompletionQuery;
import org.apache.lucene.search.suggest.document.RegexCompletionQuery;
import org.apache.lucene.search.suggest.document.SuggestField;
import org.apache.lucene.search.suggest.document.SuggestIndexSearcher;
import org.apache.lucene.search.suggest.document.TopSuggestDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;

/**
 * Version-portable history probe (main(), public API only; public reflection is used only to bridge
 * API differences between releases: the completion postings format class name and the
 * SuggestIndexSearcher#suggest signature).
 *
 * <p>RegexCompletionQuery "takes a regular expression as the prefix of the query term". With the
 * suggestions foo/5, food/1, fox/2 in one segment, "foo." and "foo[^x]" must return only food, and
 * "foo.." nothing. If the regular expression also runs over the suggester's internal end byte
 * (0x00) after an analyzed form, "foo." and "foo[^x]" also return foo, and "foo.." returns food.
 *
 * <p>Exit 0 = expected behaviour, 1 = the end-byte over-match, 2 = no verdict (control failed or an
 * unexpected result).
 */
public class RegexCompletionEndByteProbe {

  private static final String FIELD = "suggest_field";

  // newest first; the first one present is the format the release's own tests write with
  private static final String[] FORMATS = {
    "Completion104", "Completion101", "Completion912", "Completion99",
    "Completion90", "Completion84", "Completion50"
  };

  private static PostingsFormat completionFormat() throws Exception {
    for (String f : FORMATS) {
      try {
        Class<?> c = Class.forName("org.apache.lucene.search.suggest.document." + f + "PostingsFormat");
        return (PostingsFormat) c.getConstructor().newInstance();
      } catch (ClassNotFoundException e) {
        // try the next older one
      }
    }
    throw new IllegalStateException("no completion postings format found");
  }

  private static List<String> suggest(SuggestIndexSearcher searcher, String regex, int n)
      throws Exception {
    CompletionQuery query = new RegexCompletionQuery(new Term(FIELD, regex));
    TopSuggestDocs docs;
    Method m3 = null;
    try {
      m3 = SuggestIndexSearcher.class.getMethod("suggest", CompletionQuery.class, int.class, boolean.class);
    } catch (NoSuchMethodException e) {
      // releases before the skipDuplicates parameter
    }
    if (m3 != null) {
      docs = (TopSuggestDocs) m3.invoke(searcher, query, n, false);
    } else {
      Method m2 = SuggestIndexSearcher.class.getMethod("suggest", CompletionQuery.class, int.class);
      docs = (TopSuggestDocs) m2.invoke(searcher, query, n);
    }
    List<String> out = new ArrayList<>();
    for (TopSuggestDocs.SuggestScoreDoc hit : docs.scoreLookupDocs()) {
      out.add(hit.key.toString());
    }
    return out;
  }

  private static List<String> sorted(List<String> l) {
    List<String> c = new ArrayList<>(l);
    Collections.sort(c);
    return c;
  }

  private static List<String> runIndex(String[] values, int[] weights, String[] regexes, int n)
      throws Exception {
    Path tmp = Files.createTempDirectory("regex-completion-endbyte");
    List<String> results = new ArrayList<>();
    try {
      final Codec def = Codec.getDefault();
      final PostingsFormat completion = completionFormat();
      Codec codec =
          new FilterCodec(def.getName(), def) {
            @Override
            public PostingsFormat postingsFormat() {
              return new PerFieldPostingsFormat() {
                @Override
                public PostingsFormat getPostingsFormatForField(String field) {
                  return completion;
                }
              };
            }
          };
      try (Directory dir = new NIOFSDirectory(tmp)) {
        IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer());
        iwc.setCodec(codec);
        try (IndexWriter w = new IndexWriter(dir, iwc)) {
          for (int i = 0; i < values.length; i++) {
            Document doc = new Document();
            doc.add(new SuggestField(FIELD, values[i], weights[i]));
            w.addDocument(doc);
          }
          w.commit();
        }
        try (DirectoryReader reader = DirectoryReader.open(dir)) {
          System.out.println("  completion format: " + completion.getName()
              + ", segments: " + reader.leaves().size());
          SuggestIndexSearcher searcher = new SuggestIndexSearcher(reader);
          for (String regex : regexes) {
            results.add(String.join(",", suggest(searcher, regex, n)));
          }
        }
      }
    } finally {
      try (Stream<Path> s = Files.walk(tmp)) {
        s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
      } catch (IOException e) {
        // best effort
      }
    }
    return results;
  }

  private static List<String> split(String s) {
    return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
  }

  public static void main(String[] args) throws Exception {
    String[] regexes = {"fo.", "foo.", "foo[^x]", "foo.."};
    List<String> r = runIndex(new String[] {"foo", "food", "fox"}, new int[] {5, 1, 2}, regexes, 10);

    List<String> control = split(r.get(0));
    List<String> dot = split(r.get(1));
    List<String> neg = split(r.get(2));
    List<String> twoDots = split(r.get(3));
    System.out.println("  'fo.'     -> " + control + "  (control, expected foo, fox, food in any order)");
    System.out.println("  'foo.'    -> " + dot + "  (expected [food])");
    System.out.println("  'foo[^x]' -> " + neg + "  (expected [food])");
    System.out.println("  'foo..'   -> " + twoDots + "  (expected [])");

    // informational only (not part of the verdict): with several entries sharing one analyzed form,
    // a longer pattern can also run into the dedup bytes after the end byte
    List<String> d =
        runIndex(new String[] {"bar", "bar", "bar", "bar", "bar"}, new int[] {5, 4, 3, 2, 1},
            new String[] {"bar..", "ba..."}, 10);
    System.out.println("  info: 5 x 'bar': 'bar..' -> " + split(d.get(0))
        + ", 'ba...' -> " + split(d.get(1)) + "  (expected [] for both)");

    if (!sorted(control).equals(Arrays.asList("foo", "food", "fox"))) {
      System.out.println("VERDICT: no verdict (control failed)");
      System.exit(2);
    }
    int ok = 0, bug = 0, other = 0;
    // each check: expected result, and the result if the end byte is consumed
    Object[][] checks = {
      {dot, Arrays.asList("food"), Arrays.asList("foo", "food")},
      {neg, Arrays.asList("food"), Arrays.asList("foo", "food")},
      {twoDots, Arrays.asList(), Arrays.asList("food")},
    };
    for (Object[] c : checks) {
      @SuppressWarnings("unchecked")
      List<String> got = sorted((List<String>) c[0]);
      if (got.equals(c[1])) {
        ok++;
      } else if (got.equals(c[2])) {
        bug++;
      } else {
        other++;
      }
    }
    System.out.println("  checks: ok=" + ok + " endbyte-overmatch=" + bug + " other=" + other);
    if (other > 0) {
      System.out.println("VERDICT: no verdict (unexpected result)");
      System.exit(2);
    }
    if (bug > 0) {
      System.out.println("VERDICT: BUG (regex consumed the internal end byte in " + bug + " of 3 checks)");
      System.exit(1);
    }
    System.out.println("VERDICT: expected behaviour (3 of 3 checks)");
    System.exit(0);
  }
}
