# The int7_hnsw back-compat test fixtures of 10.4.0, 10.5.0, 10.5.1, 10.5.2 and 10.6.0 name the test framework's 'Asserting' codec, because #15160 put the helper that #14460 had removed back into the fixture generator; the 11.0.0 fixture would be written the same way (test sources and fixtures only, no released jar; known upstream as #14456; in 10.4.0 to 10.6.0 too)

Finding LFV-1510 · Area: backward-codecs / back-compat test fixtures (the int7_hnsw family and its generator; test sources only) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

Lucene's back-compat tests read small indexes that past releases wrote (checked-in zip files), and a release step writes a new one for each release. You expect every such file to name a codec of lucene-core or lucene-backward-codecs, as the tests' own check asks of the other families. Instead the int7_hnsw files of 10.4.0, 10.5.0, 10.5.1, 10.5.2 and 10.6.0 name 'Asserting', a codec that only Lucene's test framework has, because since #15160 the generator wraps the test framework's codec again (the state that #14456 reported and #14460 repaired in April 2025). No test fails today and no released jar is affected: the files are readable only with the test framework on the class path and only while its codec maps to formats that can still read them, and the 11.0.0 release step would write one more such file.

## Expected vs actual

- Expected: You expect every such file to name a codec of lucene-core or lucene-backward-codecs, as the tests' own check asks of the other families.
- Actual: The int7_hnsw files of 10.4.0, 10.5.0, 10.5.1, 10.5.2 and 10.6.0 name 'Asserting', a codec that only Lucene's test framework has, because since #15160 the generator wraps the test framework's codec again (the state that #14456 reported and #14460 repaired in April 2025).

## Files

- [src/TestInt7FixtureCodecName.java](src/TestInt7FixtureCodecName.java) (JUnit test)

## Run it

- Tested on Lucene main 11c8b6ea99ae (a commit of 2026-11-23): still reproduces.
- Tested on Lucene branch_11x daa54f2e0cd3 (a commit of 2026-11-13): reproduced.
- Not new in 11.0: the 10.6.0 release has it too (with the 10.6.0 release jars and the copy of the file in the 10.6.0 tag the test fails the same way), and so do Lucene main and the 11.0 release branch (branch_11x daa54f2e0cd3). It concerns Lucene's own test sources and test fixtures only: no released jar is affected and no test of Lucene's suite fails. Known upstream: #14456 (gf2121, April 2025) states the defect at its first occurrence, which was repaired then; this test shows the state that returned with #15160. What the test does: it unzips int7_hnsw.10.4.0.zip, the back-compat index that was written for the 10.4.0 release and is checked in with Lucene's test sources, reads its commit and asserts that the codec name recorded for its first segment begins with 'Lucene'. It fails with 'java.lang.AssertionError: Asserting': the message is the recorded codec name, the codec of Lucene's test framework. The outcome is the same with and without assertions, and the test uses no randomness. For the Gradle alternative at the end of this page the module is lucene/backward-codecs: the test has no package, so the file goes directly into lucene/backward-codecs/src/test/ (run in a checkout of branch_11x daa54f2e0cd3, with and without assertions: the same failure).
- Fixture: the test reads int7_hnsw.10.4.0.zip, a test resource of Lucene's backward-codecs module (lucene/backward-codecs/src/test/org/apache/lucene/backward_index/ in the checkout), so the run puts $LUCENE/lucene/backward-codecs/src/test on the class path; without it the test fails for another reason.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual, or names the exception that was thrown).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/backward-codecs/build/libs/lucene-backward-codecs-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 1
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=1 \
     -cp "classes:$LUCENE/lucene/backward-codecs/src/test:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class TestInt7FixtureCodecName
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
