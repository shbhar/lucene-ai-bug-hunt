# Hyphenation grammars whose TernaryTree key array grows past 65,535 chars load without error but silently lose or corrupt patterns, because TernaryTree stores key-array offsets in 16-bit chars (the Norwegian OFFO grammars the Lucene docs point to: nb/nn 1,158 of 27,448 patterns wrong, 1.2 no.xml 309 of 26,017)

Bug LFV-897 · Area: analysis-common / compound hyphenation (TernaryTree insert and compact via HyphenationTree.loadPatterns and HyphenationCompoundWordTokenFilter) · Priority: medium · Impact: silent wrong results · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You load a well-formed FOP-format hyphenation grammar with HyphenationCompoundWordTokenFilter.getHyphenationTree (or HyphenationTree.loadPatterns, or the factory's hyphenator file) and expect findPattern to return every pattern's values afterwards, or an exception if the grammar does not fit. For a grammar whose stored key tails pass 65,535 chars, such as the Norwegian nb.xml from the OFFO download the Lucene docs link to (27,448 patterns, 52,404 nodes), loading succeeds but 1,158 patterns return wrong values (1,111 are not found at all), and with the OFFO 1.2 Norwegian grammar real words such as 'forfatter' are hyphenated and decompounded differently. The cause: TernaryTree keeps the offset of each key tail in its key array in a 16-bit char (lo[p] = (char) kv.alloc(len + 1), TernaryTree.java:151, and (char) k in compact at:408/:410) without a range check, so past 65,535 the offset wraps and the new tail is written over an earlier one; grammars past 65,535 nodes fail with StackOverflowError instead, the part that Apache FOP's open FOP-2569 already reports.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/compound/TestHyphenationTreeKeyArrayOffsets.java](src/org/apache/lucene/analysis/compound/TestHyphenationTreeKeyArrayOffsets.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.compound.TestHyphenationTreeKeyArrayOffsets
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
