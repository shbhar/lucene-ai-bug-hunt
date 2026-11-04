# AbstractAnalysisFactory.requireChar silently cuts a mandatory char parameter longer than one char to its first char (and throws StringIndexOutOfBoundsException for an empty value) instead of rejecting it with IllegalArgumentException, as its optional twin getChar does

Finding LFV-1225 · Area: core / analysis: AbstractAnalysisFactory parameter parsing (requireChar vs getChar) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1225.html](../../bugs/LFV-1225.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You write a custom analysis factory that reads a mandatory one-char setting with requireChar(args, "delimiter"), configure it with delimiter="ab" by mistake, and expect the IllegalArgumentException that the optional getChar(args, "delimiter", '|') gives for the same value ("delimiter should be a char. \"ab\" is invalid"). Instead requireChar returns 'a' with no error, because its body is just require(args, name).charAt(0); and since require() has already removed the key, the factory's usual 'Unknown parameters' check cannot catch the bad value either. The same missing check makes an empty value throw StringIndexOutOfBoundsException rather than IllegalArgumentException, and turns a supplementary character into a lone high surrogate.

## Expected vs actual

- Expected: `IllegalArgumentException` (`delimiter should be a char. "ab" is invalid`), as `getChar` throws for the same value.
- Actual: `AssertionFailedError: Expected exception IllegalArgumentException but no exception was thrown`: `requireChar` returns `'a'`.

## Files

- [src/org/apache/lucene/analysis/TestRequireCharLength.java](src/org/apache/lucene/analysis/TestRequireCharLength.java) (JUnit test)

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
java -da -dsa -Dtests.asserts=false -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.TestRequireCharLength
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the finding page

- [TestRequireCharLength.java](TestRequireCharLength.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
