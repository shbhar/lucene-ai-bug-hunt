# Lucene 11.0 keeps RegexpQuery(Term, int, int), but its third int is now match flags instead of the determinize work limit (since #15939), and neither CHANGES.txt nor MIGRATE.md says so: an unchanged 10.x call still compiles and either matches case-insensitively without an error (limit 20,000: 3 hits instead of 1) or throws IllegalArgumentException about a flag the caller never passed (limit 10,000) (documentation defect of the unreleased 11.0; the 11.0 javadoc names the third parameter matchFlags, and the constructor treats it as that)

Finding LFV-1501 · Area: release notes (lucene/CHANGES.txt 11.0.0, lucene/MIGRATE.md 10.x -> 11.0) x search / RegexpQuery constructors (GITHUB#15939; the flag word is checked in util/automaton RegExp) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1501.html](../../bugs/LFV-1501.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Your Lucene 10.x code builds a regular-expression query with new RegexpQuery(term, RegExp.NONE, 20_000), where the third argument is the determinize work limit, and you rebuild it against Lucene 11.0 without changes. You expect a compile error, as for the other constructors that took that limit and that the 11.0 change list says were removed, or a sentence in MIGRATE.md. Instead the call still compiles and 11.0 reads the number as match flags, so 20,000 quietly makes the query case-insensitive (3 hits instead of 1 for the terms ab, AB and aB) and 10,000, the default the 10.x javadoc recommends, throws IllegalArgumentException about a removed flag you never passed. The 11.0 javadoc names the third parameter matchFlags, and the constructor treats it as that, so what is missing is one sentence in the migration notes, or the removal of this overload.

## Expected vs actual

- Expected: Count 1, as on 10.6.0 (or a compile error, as for the six other constructors that took the limit).
- Actual: `expected:<1> but was:<3>` at line 45, on main and on the 11.0 branch, assertions on and off: 20,000 is read as match flags.

## Files

- [src/org/apache/lucene/search/TestRegexpQueryThirdIntArgument.java](src/org/apache/lucene/search/TestRegexpQueryThirdIntArgument.java) (JUnit test)

## Run it

- Tested on Lucene main b5a4546a5964 (a commit of 2026-11-22): still reproduces.
- Tested on Lucene main 781025d6fe32 (a commit of 2026-11-13): reproduced.
- What the test does: with the default codec it indexes three documents whose StringField f holds ab, AB and aB, and counts the hits of new RegexpQuery(new Term("f", "ab"), RegExp.NONE, 20_000), the call as Lucene 10.x code makes it, with 20,000 as the determinize work limit. It expects 1 hit. On 11.0 the constructor reads 20,000 as match flags, which switches on case-insensitive matching, so the query matches all three terms and the test fails with 'expected:&lt;1&gt; but was:&lt;3&gt;', the same with and without assertions. On the 10.6.0 release the same test passes. The test is a witness for the gap in the release and migration notes, not a regression test for RegexpQuery: the 11.0 javadoc names the third parameter matchFlags, and the constructor treats it as that. It uses no randomness.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions off (production-like), seed DEADBEEF
java -da -dsa -Dtests.asserts=false -Xmx2g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.search.TestRegexpQueryThirdIntArgument
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestRegexpQueryThirdIntArgument.java](TestRegexpQueryThirdIntArgument.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
