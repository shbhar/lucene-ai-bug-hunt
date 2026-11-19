# Classic Highlighter returns no fragment (null) for text that contains the query term when QueryScorer or QueryTermScorer was given an IndexReader without documents: the IDF weight log(maxDoc / (docFreq + 1)) + 1 is -Infinity for maxDoc 0, so every fragment that holds the term scores -Infinity and no fragment is kept; every release from 1.9 to 10.6.0, branch_11x and main

Finding LFV-1526 · Area: highlighter / classic Highlighter (IDF-weighted term scoring: QueryScorer and QueryTermScorer with an IndexReader) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1526.html](../../bugs/LFV-1526.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You create a Highlighter with new QueryScorer(query, reader, field), or new QueryTermScorer(query, reader, field), where the reader is an index that holds no document, and call getBestFragment on the text 'alpha beta' for a TermQuery on alpha. You expect '&lt;B&gt;alpha&lt;/B&gt; beta', which the same call returns without the reader argument or with a reader that holds a document: the reader is documented as the source of IDF weights for scoring fragments, and getBestFragment is documented to return null only 'if no terms found'. Instead it returns null, with no exception: for maxDoc() 0 the IDF factor log(maxDoc / (docFreq + 1)) + 1 is -Infinity, so the term's weight and the score of every fragment that holds the term are -Infinity, and Highlighter keeps only fragments with a score above 0. Every release since 1.9 does it, and it shows only while the reader holds no document at all.

## Expected vs actual

- Expected: `getBestFragment` returns `<B>alpha</B> beta`, as it does without the reader argument.
- Actual: It returns null: `java.lang.AssertionError: expected:<<B>alpha</B> beta> but was:<null>`.

## Files

- [src/org/apache/lucene/search/highlight/TestHighlighterIdfOnEmptyIndex.java](src/org/apache/lucene/search/highlight/TestHighlighterIdfOnEmptyIndex.java) (JUnit test)

## Run it

- Tested on Lucene main 9965d34e3c77 (2026-11-16): reproduced, with assertions on and off.
- Tested on Lucene branch_11x 9d2b5a15427a (2026-11-16): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- Tested on Lucene 10.6.0 (4ae946e5a9b9) (2026-10-26): reproduced, with assertions on and off, on the 10.6.0 release jars, which are built from this commit (the tag releases/lucene/10.6.0).
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced, with assertions on and off.
- Tested on Lucene branch_11x daa54f2e0cd3 (2026-11-13): reproduced, with assertions on and off (11.0.0-SNAPSHOT).
- What each build does: the test fails the same way on Lucene main, on the 11.0 release branch (branch_11x) and with the 10.6.0 release jars, with and without assertions; the finding's record lists every release from 1.9 on. What the test does: it creates an index with the default IndexWriterConfig and closes it without adding a document, opens a reader on it (maxDoc() is 0), builds a Highlighter with new QueryScorer(query, reader, "f") for a TermQuery on alpha, and expects getBestFragment to return the text 'alpha beta' with alpha marked up, as the same call does without the reader argument. It fails with java.lang.AssertionError: expected:&lt;&lt;B&gt;alpha&lt;/B&gt; beta&gt; but was:&lt;null&gt;: no fragment comes back. Only a reader that holds no document does this: with one document in the index the same call returns the fragment (the finding page has those runs). The text and the query are fixed; the test framework draws its analyzer's settings, its directory and its environment from the seed, and the test failed the same way with every seed that was run. On a checkout of the 11.0 branch set V=11.0.0-SNAPSHOT; for the release, put the six 10.6.0 jars from Maven Central (org.apache.lucene: lucene-core, lucene-codecs, lucene-test-framework, lucene-highlighter, lucene-memory, lucene-queries) in place of the six $LUCENE jars. For the Gradle alternative at the end of this page the module is lucene/highlighter; without -Ptests.asserts the build of main and of branch_11x draws the assertion setting from its seed (on for the seed DEADBEEF), and the build of the 10.6.0 sources runs with assertions on; the test fails with either setting.
- This finding was re-run on Lucene main ec3b3cca8f10 on 2026-11-18: still reproduces.
- How to read the outcome: a failing test is the finding reproducing: the run reports 1 test found and 1 test failed, with java.lang.AssertionError: expected:&lt;&lt;B&gt;alpha&lt;/B&gt; beta&gt; but was:&lt;null&gt;.
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/highlighter/build/libs/lucene-highlighter-$V.jar
CP=$CP:$LUCENE/lucene/memory/build/libs/lucene-memory-$V.jar
CP=$CP:$LUCENE/lucene/queries/build/libs/lucene-queries-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.highlight.TestHighlighterIdfOnEmptyIndex
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestHighlighterIdfOnEmptyIndex.java](TestHighlighterIdfOnEmptyIndex.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
