# CheckIndex.doCheck with a CheckIndex.Options from its public constructor checks no segment and returns 0 (clean) for an index with a corrupt segment, because the default onlySegments is an empty list that only parseOptions turns into null ('check every segment') (every release since 5.5.0)

Finding LFV-1260 · Area: index / CheckIndex programmatic entry point (Options, doCheck) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1260.html](../../bugs/LFV-1260.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You build a CheckIndex.Options with its public constructor, give it an output stream with setOut (its only public setter), and call doCheck on an index whose only segment has one flipped byte in its stored-fields file. You expect 1, because doCheck's javadoc says '@return 0 iff the index is clean, 1 otherwise' and the same index gives 1 when the Options come from parseOptions. Instead doCheck returns 0: the constructor leaves onlySegments as an empty list, doCheck passes it unchanged to checkIndex(List), and the log shows 'Checking only these segments::' with nothing listed, followed by 'No problems were detected with this index.'

## Expected vs actual

- Expected: `doCheck` returns 1: segment `_0` has lost `_0.fdt`, and `checkIndex()` on the same index reports 1 bad segment.
- Actual: `doCheck` returns 0 (`expected:<1> but was:<0>`), with assertions on and off.

## Files

- [src/org/apache/lucene/index/TestCheckIndexDefaultOptions.java](src/org/apache/lucene/index/TestCheckIndexDefaultOptions.java) (JUnit test)

## Run it

- Tested on Lucene main 5adff78bb168 (2026-11-02): reproduced.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -XX:ActiveProcessorCount=2 -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.index.TestCheckIndexDefaultOptions
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestCheckIndexDefaultOptions.java](TestCheckIndexDefaultOptions.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
