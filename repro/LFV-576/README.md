# Test-framework ForceMergePolicy, documented as 'only returns forced merges', still lets the wrapped policy's natural merges run on commit and NRT reopen, because it inherits FilterMergePolicy's forwarding of findFullFlushMerges (regression since 9.3.0)

Bug LFV-576 · Area: test-framework / merge policies (ForceMergePolicy; FilterMergePolicy forwarding of MergePolicy.findFullFlushMerges for merge-on-commit and merge-on-getReader) · Priority: low · Impact: minor · Novelty: known upstream

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You wrap a merge policy in the test framework's ForceMergePolicy, documented as 'A MergePolicy that only returns forced merges' for when you 'wish to disallow background merges', keep the rest of IndexWriterConfig at its defaults, and commit or open an NRT reader after each of several small flushes. You expect every flushed segment to stay until you call forceMerge, as with NoMergePolicy. Instead ForceMergePolicy only nulls findMerges and inherits FilterMergePolicy's forward of findFullFlushMerges, whose MergePolicy default runs the wrapped policy's own natural merges of small segments, so with the default 500 ms merge-on-commit wait 25 one-document commits leave 1 segment instead of 25 (and an NRT reader after 25 flushes has 1 leaf).

## Expected vs actual

- Expected: You expect every flushed segment to stay until you call forceMerge, as with NoMergePolicy.
- Actual: ForceMergePolicy only nulls findMerges and inherits FilterMergePolicy's forward of findFullFlushMerges, whose MergePolicy default runs the wrapped policy's own natural merges of small segments, so with the default 500 ms merge-on-commit wait 25 one-document commits leave 1 segment instead of 25 (and an NRT reader after 25 flushes has 1 leaf).

## Files

- [src/org/apache/lucene/tests/index/TestForceMergePolicyFullFlushMerges.java](src/org/apache/lucene/tests/index/TestForceMergePolicyFullFlushMerges.java) (JUnit test)

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
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.tests.index.TestForceMergePolicyFullFlushMerges
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
