# HTMLStripCharFilter (since 10.5.0) treats a start tag whose name only begins with script, style or br (&lt;scripts&gt;, &lt;style-guide&gt;, &lt;stylesheet&gt;, &lt;brand&gt;) as a script, style or br tag, so it silently drops all text after it up to the next &lt;/script&gt; or &lt;/style&gt; (often to the end of the value) and misapplies escapedTags to such names

Bug LFV-780 · Area: analysis / HTMLStripCharFilter (JFlex grammar: the BR, SCRIPT and STYLE start-tag rules of the LEFT_ANGLE_BRACKET state; also the 'htmlStrip' char filter SPI) · Priority: medium · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-780.html](../../bugs/LFV-780.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You strip '&lt;scripts&gt;hello&lt;/scripts&gt; world' (or '&lt;style-guide&gt;hello&lt;/style-guide&gt; world') with HTMLStripCharFilter and expect '\nhello\n world', which is what Lucene 10.4.0 gives and what main gives for the same shape with any other name, such as '&lt;scrapes&gt;'. Since 10.5.0 the output is empty: the start-tag rules for script and style no longer require a delimiter after the name, so the longer name is taken for a &lt;script&gt; or &lt;style&gt; tag and everything up to the next literal &lt;/script&gt; or &lt;/style&gt;, here the end of the input, is dropped (for '&lt;stylesheet&gt;hello&lt;/stylesheet&gt; world' a StandardTokenizer after the filter emits no token at all). With escapedTags={brand}, '&lt;brand&gt;Acme&lt;/brand&gt;' likewise comes out as '\nAcme&lt;/brand&gt;' instead of unchanged.

## Expected vs actual

- Expected: `<scripts>hello</scripts> world` strips to `\nhello\n world`, as the `<scrapes>` control does, and `<brand>Acme</brand>` with `escapedTags={brand}` stays unchanged.
- Actual: It strips to an empty string, the tokenizer test gets no token, and `<brand>Acme</brand>` becomes `\nAcme</brand>`: 4 of the 5 new tests fail.

## Files

- [src/org/apache/lucene/analysis/charfilter/TestHTMLStripTagNamePrefix.java](src/org/apache/lucene/analysis/charfilter/TestHTMLStripTagNamePrefix.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.charfilter.TestHTMLStripTagNamePrefix
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Files shown on the bug page

- [TestHTMLStripCharFilter-prefix-methods.java](TestHTMLStripCharFilter-prefix-methods.java)
- [test-main.diff](test-main.diff)
- [TestHTMLStripTagNamePrefix.java](TestHTMLStripTagNamePrefix.java)
- [PrefixProbe.java](PrefixProbe.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
