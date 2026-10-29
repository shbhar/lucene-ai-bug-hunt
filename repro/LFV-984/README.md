# BPIndexReorderer's class javadoc 'Typical usage' example calls reorderer.setForkJoinPool(pool), removed in 10.0.0, and the 2-argument reorder(reader, targetDir), removed in 9.12.0, so it names APIs that no longer exist; #13289 moved the class to an Executor argument, reorder(CodecReader, Directory, Executor), and left the example unchanged (documentation defect)

Bug LFV-984 · Area: misc / index reordering (BPIndexReorderer class javadoc example) · Priority: low · Impact: minor · Novelty: new

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

The BPIndexReorderer javadoc shows 'typical usage': build a ForkJoinPool, hand it to reorderer.setForkJoinPool(pool), then call reorderer.reorder(reader, targetDir). If you copy that into your code it does not compile on any release since 9.12.0: setForkJoinPool no longer exists (removed in 10.0.0) and reorder now needs a third argument, the Executor to run on. Passing the same pool as that third argument (and deleting the setter line) compiles and works, so only the code shown in the javadoc is wrong.

The test shows that the two `BPIndexReorderer` calls in its class javadoc example, `reorderer.setForkJoinPool(pool)` and `reorderer.reorder(SlowCodecReaderWrapper.wrap(reader), targetDir)`, do not compile against main when they are given declared inputs.

## Expected vs actual

Expected: they compile. Actual: javac reports `cannot find symbol method setForkJoinPool(ForkJoinPool)` and `reorder... required: CodecReader,Directory,Executor; found: CodecReader,Directory`.

## Files

- [src/org/apache/lucene/misc/index/TestBPIndexReordererJavadocExample.java](src/org/apache/lucene/misc/index/TestBPIndexReordererJavadocExample.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/misc/build/libs/lucene-misc-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.misc.index.TestBPIndexReordererJavadocExample
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

Formal models and scripts this bug's part of the hunt used: [../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/](../../models/javadoc-package-and-overview-examples-call-removed-apis-siblings-of-16904/)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
