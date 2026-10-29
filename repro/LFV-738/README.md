# UAX29URLEmailTokenizer does not emit an ftp URL with no path ('ftp://host') as one URL token, although its http rule and RFC 1738 make the path optional

Bug LFV-738 · Area: analysis / UAX29URLEmailTokenizer JFlex URL rules (FTPurl production; analysis-common) · Priority: low · Impact: silent wrong results · Novelty: new

Bug page: [../../bugs/LFV-738.html](../../bugs/LFV-738.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

You run text such as 'get it from ftp://ftp.example.com today' or 'ftp://localhost' through UAX29URLEmailTokenizer, whose javadoc says URLs are 'tokenized according to the relevant RFCs', and expect each ftp URL as one &lt;URL&gt; token, because RFC 1738 (section 3.2.2: 'The whole url-path may be omitted, including the "/" delimiting it') and RFC 3986 (an empty path after an authority) allow it, and the same tokenizer already keeps 'http://localhost' whole. Instead it emits 'ftp' as &lt;ALPHANUM&gt; followed by the rest on its own ('ftp.example.com' as &lt;URL&gt;, 'localhost' as &lt;ALPHANUM&gt;, '192.0.2.10' as &lt;NUM&gt;, '//anonymous@ftp.example.org' as &lt;EMAIL&gt;), because the grammar's FTPurl rule makes the '/'-led FTPorFILEpath mandatory while the http rule beside it makes its path optional. With a trailing slash ('ftp://ftp.example.com/') the URL is one token, and nothing reports an error.

The test shows that `UAX29URLEmailTokenizer` does not keep the path-less ftp URL `ftp://localhost` as one `<URL>` token (RFC 1738 3.2.2 makes the ftp path optional).

## Expected vs actual

Expected one token `ftp://localhost` of type `<URL>`; actual `ftp` + `localhost`: `term 0 expected:<ftp[://localhost]> but was:<ftp[]>` (-ea and -da).

## Files

- [src/org/apache/lucene/analysis/email/TestFtpUrlWithoutPath.java](src/org/apache/lucene/analysis/email/TestFtpUrlWithoutPath.java) (JUnit test)

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
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed DEADBEEF
java -ea -esa --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=DEADBEEF \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.email.TestFtpUrlWithoutPath
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=false` for a run without assertions).

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
