# IndexMergeTool's '--' end-of-options branch compares Strings with ==, so a command-line '--' is rejected as an unrecognized option, and if the branch ever fires it leaves '--' as the target path

Bug LFV-339 · Area: misc / IndexMergeTool command-line tool (option parsing) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run the IndexMergeTool command line with '--' before the paths, for example 'IndexMergeTool -- -merged src1 src2', and expect the parser's dedicated '--' branch (IndexMergeTool.java:61) to end option parsing, as the usual command-line convention says, so that '-merged' is taken as the target directory. Instead the branch compares the argument with == against the literal "--", which is never true for a command-line argument, so '--' falls through to the option switch and the tool stops with "unrecognized option: '--'" and exit code 2 without merging anything. The branch is also wrong when it does fire (only when Java code passes the literal "--" to main): it stops without skipping the '--', so '--' becomes the merged-index path and the intended target is read as the first source index.

## Expected vs actual

- Expected: The test passes: it asserts the documented behaviour.
- Actual: The branch compares the argument with == against the literal "--", which is never true for a command-line argument, so '--' falls through to the option switch and the tool stops with "unrecognized option: '--'" and exit code 2 without merging anything.

## Files

- [src/org/apache/lucene/misc/TestIndexMergeToolEndOfOptions.java](src/org/apache/lucene/misc/TestIndexMergeToolEndOfOptions.java) (JUnit test)

## Run it

- Tested on Lucene main a9779ef3c193 (2026-10-26): still reproduces.
- Tested on Lucene main ea57360912ed (2026-10-24): reproduced.
- How to read the outcome: a failing test is the bug reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=11.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.TestIndexMergeToolEndOfOptions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
