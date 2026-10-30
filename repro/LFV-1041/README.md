# The org.apache.lucene.analysis.compound package overview's three code examples do not compile on any release since 4.0.0: they call HyphenationCompoundWordTokenFilter.getHyphenationTree(Reader), WhitespaceTokenizer(Reader) and the String dictionary constructors of both decompounding filters, all removed; their loop never calls reset, and the page's output table shows per-part offsets that the filters stopped emitting in 4.4.0 (documentation defect)

Bug LFV-1041 · Area: analysis-common / compound (package overview examples and output table, package-info.java) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The compound package overview shows how to split German and Swedish compound words with HyphenationCompoundWordTokenFilter and DictionaryCompoundWordTokenFilter, in three examples and a table of the tokens the filter produces. Copied into code, none of the three examples compiles on any release since 4.0.0, because getHyphenationTree(Reader), WhitespaceTokenizer(Reader) and the String dictionary constructors no longer exist (today: getHyphenationTree(String or InputSource), new WhitespaceTokenizer plus setReader, and a CharArraySet dictionary), and once ported the loop throws IllegalStateException because it never calls reset. With those fixes the examples run, but every word part carries the offsets of the whole word, for example (Rind,0,29) where the table shows (Rind,0,4), a deliberate 4.4.0 change (LUCENE-5018) the table never picked up; only the page is wrong.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: While the bug is present the test fails; the failure message states expected against actual.

## Files

- [src/org/apache/lucene/analysis/compound/TestCompoundPackageDoc.java](src/org/apache/lucene/analysis/compound/TestCompoundPackageDoc.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
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

# run 1: assertions on, seed DEADBEEF, default flags
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.compound.TestCompoundPackageDoc
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/analysis-benchmark-backward-codecs-javadoc-examples-compile-on-main/](../../models/analysis-benchmark-backward-codecs-javadoc-examples-compile-on-main/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
