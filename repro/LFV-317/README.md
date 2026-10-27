# HTMLStripCharFilter reads a backslash right before a quoted attribute value's closing quote as an escaped quote, so the tag runs on to the next quote of the same kind and the visible text in between is dropped (every quoted attribute since 9.9.0 / GITHUB#11724; on* event attributes since the 2012 JFlex rewrite)

Bug LFV-317 · Area: analysis / HTMLStripCharFilter (JFlex grammar: SingleQuoted, DoubleQuoted, OpenTagAttribute, EventAttribute) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-317.html](../../bugs/LFV-317.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You strip '&lt;a href="C:\Temp\"&gt;folder&lt;/a&gt; text after &lt;a href="x"&gt;y&lt;/a&gt; end' with HTMLStripCharFilter and expect 'folder text after y end', which is what 9.8.0, the HTML parsing rules (Python's html.parser as an independent check) and the same document without the trailing backslash give. Instead main returns 'y end': the scanner reads the backslash and the closing quote as an escaped quote, so the attribute value runs on to the next double quote (the one in href="x"), everything from the first tag to the end of the second tag is treated as one tag, and 'folder text after' is never indexed. No error is raised, and a single-quoted value ending in a backslash loses text the same way.

## Expected vs actual

- Expected: All three trigger inputs strip to `folder text after y end`, as the control does.
- Actual: Each strips to `y end`: 40 tests, 3 failed, with assertions on and off; the control passes.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestHTMLStripAttributeBackslash.java](src/org/apache/lucene/analysis/charfilter/TestHTMLStripAttributeBackslash.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.charfilter.TestHTMLStripAttributeBackslash
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestHTMLStripCharFilter-backslash-methods.java](TestHTMLStripCharFilter-backslash-methods.java)
- [test-main.diff](test-main.diff)
- [TestHTMLStripAttributeBackslash.java](TestHTMLStripAttributeBackslash.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
