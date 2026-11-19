# In no release (10.6.0 does not have it; 11.0.0 would be the first): branch_11x and main carry an older top-level-domain table for UAX29URLEmailTokenizer than the 10.6.0 release, so Lucene 11.0 would ship it; an e-mail address under '.web' is one EMAIL token on 10.6.0 and two word tokens on branch_11x and main, so after the upgrade an analyzed search for it no longer matches the token that 10.6.0 indexed (a generated data table that fell behind, not a logic error)

Finding LFV-1508 · Area: analysis / UAX29URLEmailTokenizer generated top-level-domain table (ASCIITLD.jflex and the scanner generated from it; UAX29URLEmailAnalyzer and the 'uax29UrlEmail' tokenizer factory; analysis-common) · Priority: low · Impact: minor · Novelty: new

Finding page: [../../bugs/LFV-1508.html](../../bugs/LFV-1508.html)

AI-assisted finding, confirmed by automated checks, not yet triaged by a human Lucene committer. This site files nothing upstream.

## What it shows

In no release: 10.6.0 does not have it and 11.0.0 would be the first release with it (it is on branch_11x and main). You analyze text that holds an e-mail address under the top-level domain 'web', for example user@example.web, with UAX29URLEmailTokenizer (or UAX29URLEmailAnalyzer or the 'uax29UrlEmail' factory), and you move from Lucene 10.6.0 to a build of the 11.0 branch or of main. You expect what 10.6.0 gives, one &lt;EMAIL&gt; token, because the domain is in the IANA list that the tokenizer's domain table is generated from. Instead, the 11.0 branch and main give the two word tokens user and example.web, because their copy of that generated table is from 2026-06-19, older than the copy of 2026-09-03 in 10.6.0, and lacks this one domain. An analyzed search for the address then no longer matches the token that 10.6.0 indexed, with no error (the query becomes the two words user and example.web, so with the classic query parser's default operator (OR) it can still match a document that holds either word; in a probe on an index of three documents written by 10.6.0 it finds 1 document on 10.6.0 and none on branch_11x and main); it is a data table that fell behind on main, not a logic error, and the repair is the project's routine regeneration of the table and the scanner; when this finding was first written up, that regeneration had not been run for it. Update 2026-11-16: the scanner step of that regeneration has since been run for this finding, on 2026-11-15, outside the build and with the table of the 10.6.0 tag. With the regenerated scanner in place of the committed one, user@example.web is one &lt;EMAIL&gt; token on branch_11x and main and the repro test passes, with -ea and -da. In that run the gradle task itself was not run, and the table was not IANA's list of that day. Update 2026-11-17: the gradle task has since been run, forced and with IANA's list of that day, in a scratch checkout of pull request #17035 (open when read on 2026-11-18; it changes the build's JFlex regeneration); with the scanner from that run in place of the committed one the repro test passes on branch_11x 89e68e0f3a50 and main 596ef11ddf96, with -ea and -da.

## Expected vs actual

- Expected: One token, `user@example.web` of type `<EMAIL>`, as on 10.6.0.
- Actual: `term 0 expected:<user[@example.web]> but was:<user[]>` at line 28, on main and on the 11.0 branch, assertions on and off: the tokens are `user` and `example.web`.

## Files

- [src/org/apache/lucene/analysis/email/TestEmailUnderWebTopLevelDomain.java](src/org/apache/lucene/analysis/email/TestEmailUnderWebTopLevelDomain.java) (JUnit test)

## Run it

- Tested on Lucene main ec3b3cca8f10 (2026-11-18): still reproduces.
- Tested on Lucene main 781025d6fe32 (2026-11-13): reproduced.
- In no release: the test fails on Lucene main and on the 11.0 release branch (branch_11x daa54f2e0cd3), with and without assertions, and passes on the 10.6.0 release jars, so 11.0.0 would be the first release with it. It is a generated data table that fell behind on main, not a logic error: main and branch_11x carry the top-level-domain table of 2026-06-19, the 10.6.0 release carries the table of 2026-09-03, and the two differ in one entry, 'web'. What the test does: it gives 'user@example.web' to new UAX29URLEmailTokenizer() and expects what 10.6.0 returns, the one token user@example.web of type &lt;EMAIL&gt;. On main and branch_11x the tokenizer returns the two tokens user and example.web, so the test fails with 'term 0 expected:&lt;user[@example.web]&gt; but was:&lt;user[]&gt;'. The releases before 10.6.0 fail it too (run on 10.5.2 and 9.12.3): their tables are older than the domain, which IANA delegated on 2026-07-23; that is the ordinary age of a snapshot and not this finding. The test uses no randomness and no index.
- How to read the outcome: a failing test is the finding reproducing (the failure message states expected against actual).
- Seeds: a rerun with the same seed replays the same random choices. Assertions off (-da -dsa -Dtests.asserts=false) is the production-like setting.

```bash
# Lucene checkout at the tested commit, built with: ./gradlew --no-daemon assemble   (JDK 25)
LUCENE=/path/to/lucene; V=12.0.0-SNAPSHOT
CP=$LUCENE/lucene/core/build/libs/lucene-core-$V.jar
CP=$CP:$LUCENE/lucene/codecs/build/libs/lucene-codecs-$V.jar
CP=$CP:$LUCENE/lucene/test-framework/build/libs/lucene-test-framework-$V.jar
CP=$CP:$LUCENE/lucene/analysis/common/build/libs/lucene-analysis-common-$V.jar
CP=$CP:randomizedtesting-runner-2.9.2.jar:junit-4.13.2.jar:hamcrest-3.0.jar   # test-framework dependencies (Maven Central)
CP=$CP:junit-platform-console-standalone-6.1.3.jar   # JUnit Platform console launcher (Maven Central)

javac -d classes -cp "$CP" $(find src -name '*.java')

# run 1: assertions on, seed 0787A001
java -ea -esa -Xmx1g --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -Dtests.seed=0787A001 \
     -cp "classes:$CP" org.junit.platform.console.ConsoleLauncher \
     execute --select-class org.apache.lucene.analysis.email.TestEmailUnderWebTopLevelDomain
```

Gradle alternative: copy the test into the matching module's `src/test/` tree of the checkout and run `./gradlew -p lucene/<module> test --tests <class> -Ptests.seed=<seed>` (add `-Ptests.asserts=true` for a run with assertions and `-Ptests.asserts=false` for one without: on main and on the 11.0 branch the build takes the default from the seed).

## Files shown on the finding page

- [TestEmailUnderWebTopLevelDomain.java](TestEmailUnderWebTopLevelDomain.java)

## Feedback

Open an issue in [lucene-ai-bug-hunt](https://github.com/shbhar/lucene-ai-bug-hunt/issues). Please do not open issues or pull requests in apache/lucene about this finding unless a Lucene committer agrees it should be filed.
