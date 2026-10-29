# FuzzyQuery on the empty term matches only the empty term

Bug LFV-024 · Area: search / multi-term queries · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-024.html](../../bugs/LFV-024.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run a FuzzyQuery whose term text is empty. You expect it to match every indexed term within the edit distance, as the LUCENE-7439 contract says. Instead it matches only the empty term.

The test shows that `FuzzyQuery(new Term("f", ""), 1)` on an index with the terms `""` and `"a"` matches only `""`.

## Expected vs actual

Expected: 2 hits, since `"a"` is one edit from `""` (constructor javadoc: "will match terms with an edit distance of at most maxEdits"). Actual (`-ea` and `-da`): `expected:<2> but was:<1>`.

## Files

- [src/org/apache/lucene/search/TestFuzzyQueryEmptyTermText.java](src/org/apache/lucene/search/TestFuzzyQueryEmptyTermText.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestFuzzyQueryEmptyTermText
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
