# PorterStemFilter's and StempelFilter's class javadocs still tell readers to use LowerCaseTokenizer, which was removed in 8.0.0 (LUCENE-8498), and PorterStemFilter's MyAnalyzer example calls new LowerCaseTokenizer(version, reader), a constructor form gone since 5.0.0, so the example does not compile; the documented replacement is LetterTokenizer followed by LowerCaseFilter (documentation defect)

Bug LFV-987 · Area: analysis / stemming filters (PorterStemFilter and StempelFilter class javadoc) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

PorterStemFilter's javadoc says its input must already be lower case, tells you to use LowerCaseFilter or LowerCaseTokenizer, and shows a MyAnalyzer built on new LowerCaseTokenizer(version, reader); StempelFilter's javadoc gives the same advice. LowerCaseTokenizer was removed in Lucene 8.0.0 (the migration notes say to use LetterTokenizer followed by LowerCaseFilter) and its (Version, Reader) constructor was already gone in 5.0.0, so the example does not compile and the advice names a class that no longer exists. Written with LetterTokenizer and LowerCaseFilter, the example compiles and stems 'Running Dogs' to [run, dog], so only the documentation is wrong.

The test shows that the `LowerCaseTokenizer` that the `PorterStemFilter` (and `StempelFilter`) class javadoc recommends, and that its `MyAnalyzer` example constructs, no longer exists: it was removed in 8.0.0 (LUCENE-8498), so the example cannot compile (a documentation defect).

## Expected vs actual

Expected (javadoc): the class `LowerCaseTokenizer` exists. Actual: `ClassNotFoundException: org.apache.lucene.analysis.core.LowerCaseTokenizer` (its package in 4.0-7.x; no Lucene jar on main has a class of that name) (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/en/TestPorterStemFilterJavadocTokenizer.java](src/org/apache/lucene/analysis/en/TestPorterStemFilterJavadocTokenizer.java) (JUnit test)

## Run it

- Tested on Lucene main 645d4fe14a20 (2026-10-28): reproduced.
- Recorded outcome of run 1 on Lucene main 645d4fe14a20: 1 test(s) run, 1 failed.
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
     execute --select-class org.apache.lucene.analysis.en.TestPorterStemFilterJavadocTokenizer
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
