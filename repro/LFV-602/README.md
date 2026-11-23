# UnifiedHighlighter and MatchRegionRetriever throw IllegalArgumentException on a MultiReader whose indexes differ in any field's schema, even only in norms or in an unrelated field, because FieldInfos.getMergedFieldInfos applies the single-index consistency check across the indexes

Finding LFV-602 · Area: highlighter / uhighlight + matchhighlight: default offset-source choice on composite readers (core index FieldInfos.getMergedFieldInfos) · Priority: medium · Impact: crash · Novelty: known outside Lucene

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build two indexes in which a field is declared slightly differently, for example 'body' with norms in one and without norms in the other (both with offsets), open them together as new MultiReader(r1, r2), search body:alpha and get both hits. You expect UnifiedHighlighter.highlight("body", query, topDocs) to return both snippets ('&lt;b&gt;alpha&lt;/b&gt; beta', '&lt;b&gt;alpha&lt;/b&gt; gamma'), as it does when both indexes use the same options or when each index is highlighted on its own. Instead it throws IllegalArgumentException 'cannot change field "body" from omitNorms=false to inconsistent omitNorms=true' (and 'cannot change field "tag" ...' when only an unrelated field differs), because the default offset-source choice builds FieldInfos.getMergedFieldInfos over the whole MultiReader, which applies the per-index field-consistency check across the two indexes; MatchRegionRetriever's default strategies fail the same way.

## Expected vs actual

- Expected: You expect UnifiedHighlighter.highlight("body", query, topDocs) to return both snippets ('&lt;b&gt;alpha&lt;/b&gt; beta', '&lt;b&gt;alpha&lt;/b&gt; gamma'), as it does when both indexes use the same options or when each index is highlighted on its own.
- Actual: It throws IllegalArgumentException 'cannot change field "body" from omitNorms=false to inconsistent omitNorms=true' (and 'cannot change field "tag" ...' when only an unrelated field differs), because the default offset-source choice builds FieldInfos.getMergedFieldInfos over the whole MultiReader, which applies the per-index field-consistency check across the two indexes; MatchRegionRetriever's default strategies fail the same way.

## Files

- [src/org/apache/lucene/search/uhighlight/TestHighlightMultiReaderMixedFieldOptions.java](src/org/apache/lucene/search/uhighlight/TestHighlightMultiReaderMixedFieldOptions.java) (JUnit test)
- [src/repro/HighlightMultiReaderMixedOptionsMain.java](src/repro/HighlightMultiReaderMixedOptionsMain.java) (main() class)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene main a9779ef3c193 (a commit of 2026-10-26): reproduced.
- src/repro/HighlightMultiReaderMixedOptionsMain.java, the main() class of run 2, calls the constructor new UnifiedHighlighter(searcher, analyzer), which #16975 (merged 2026-11-05) removed on main (d060c053bd8) and on branch_11x (2e62f85f6c5). So on a build of main or of branch_11x the javac line below reports one error, at line 64 of that file, and run 2 cannot load its class; whether javac still writes the class of the JUnit test depends on the order in which find lists the two files (in runs of 2026-11-22 it wrote that class with the test file first and no class with the other file first). Leave the file out there: write find src/org in place of find src in the javac line and leave out run 2. Run 1 is the repro: run on 2026-11-22 on a build of main b5a4546a5964 and on a build of branch_11x 262e019500ec (there V=11.0.0-SNAPSHOT), with assertions on and off, it reports 5 tests found and 4 failed, each with an AssertionError whose message carries that of the IllegalArgumentException (testNormsDifferOnly: 'highlighting a searchable MultiReader threw: cannot change field "body" from omitNorms=false to inconsistent omitNorms=true'), and testControlConsistentOptions passes. With the jars of the 10.6.0 release (Maven Central, org.apache.lucene, in place of the six $LUCENE jars; V=10.6.0), where that constructor exists and is deprecated, the block runs as printed, with assertions on and off: run 1 reports the same 4 failures of 5 tests, and run 2 prints a line that begins 'control-consistent: ok' and three lines that begin 'norms-differ-only: FAIL', 'offsets-in-one-index: FAIL' and 'unrelated-field-differs: FAIL' and exits with status 1. With the jars of 9.12.3 the JUnit test does not compile and the main() class alone is the repro (find src/repro in the javac line, run 2 only): it prints the same four lines there and, with the jars of 8.11.4, FAIL for offsets-in-one-index only (run the same day with assertions on).
- How to read the outcome: a failing test is the finding reproducing: run 1, with the javac line as the note above says for the build, reports 5 tests found and 4 tests failed, each of the 4 with an AssertionError whose message ends with that of the IllegalArgumentException the test caught (it begins cannot change field); with the jars of the 10.6.0 release the main() class of run 2 prints a line that begins control-consistent: ok and three lines that begin norms-differ-only: FAIL, offsets-in-one-index: FAIL and unrelated-field-differs: FAIL, and exits with status 1, without an uncaught exception.
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

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.uhighlight.TestHighlightMultiReaderMixedFieldOptions

# run 2: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" repro.HighlightMultiReaderMixedOptionsMain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
