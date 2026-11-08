# AI-assisted bug hunt in Apache Lucene

A dated snapshot (2026-11-04) of AI-assisted work: findings in [Apache Lucene](https://github.com/apache/lucene). The findings the hunt made and confirmed went through AI-run checks. This is an independent list: per the Apache Lucene project’s AI policy, nothing here is filed in Lucene unless a person files it by hand.

**Want to run a hunt like this in your own open-source project?** Start with the [how-to](https://shbhar.github.io/lucene-ai-bug-hunt/howto.html) (principles, methods and pipeline), and give your AI agent [HOWTO.md](HOWTO.md), the same method written as instructions.

Browse the table: open `index.html` (published with GitHub Pages).

## What is in this snapshot

- 1322 registered findings: 1066 new, for which the automated prior-report search found no earlier report (a keyword search; private reports are not visible), and 256 already known (187 upstream, 69 outside Lucene), where others reported or discussed the same behaviour (tracked, but not counted as new).
- 2 of them are withheld pending security review: they appear in the table and in every count, but only with their id, module, priority tier, novelty (new or already known) and the line "Details withheld pending security review".
- Priority tiers: 9 high, 256 medium, 1057 low, 0 not yet ranked.
- 1070 public finding pages (`bugs/`), 1320 repro directories (`repro/`) and 168 units of formal models and scripts (`models/`).
- The full table as data: `data/bugs.csv` and `data/bugs.json`.

## Share a filtered view

The table keeps its filters, search and sort in the page address, so a link opens the same view; "Link to this view" and "Copy link", beside Reset, give the link to the current view. For example: [the findings new in 10.6](https://shbhar.github.io/lucene-ai-bug-hunt/?new106=1#bugs), [Medium priority only](https://shbhar.github.io/lucene-ai-bug-hunt/?priority=medium#bugs), or [a search for mmap](https://shbhar.github.io/lucene-ai-bug-hunt/?q=mmap#bugs).

## How the findings were found

The hunt used AI models (Anthropic Claude Opus and OpenAI GPT-5.6) with formal methods (TLA+/TLC model checking, Alloy, Z3/cvc5 SMT solving, Dafny proofs and bounded model checking) alongside fuzzing, model-based, property-based and differential testing, and static analysis. Of the 1064 new findings the hunt made that are shown in full, a formal tool's own output gave the first signal for 44; a semi-formal check (a check of the real jar against exact semantics over a bounded input space, exhaustive or for some harnesses randomly sampled, or a hypothesis formed while modelling that a tool then witnessed) for 678; code reading or other testing for 315; and a testing baseline for 27. Every finding ends in a minimal executable test that fails while the reported behaviour is present (on Lucene main, or on the release branch for a finding only a release branch has).

## Lucene version

The hunt began on Lucene main ea57360912ed (2026-10-24). On 2026-11-02, 1053 of the repros then published were set up for a rerun on main e1faeba08bde. 1053 ran: 1051 still reproduced (1051 unchanged) and 2 no longer failed because upstream fixed them. Since then, 731 of the findings shown in full got a smaller repro, one minimal test, which replaces their earlier repro files. The repros published now were last run on these commits: 1074 on e1faeba08bde, 242 on 5adff78bb168, 2 on 645d4fe14a20, 1 on ea57360912ed, 1 on 716d6f216f52; each repro page says where its repro was run, or that no run is on file.

## Reproduce a finding

Each `repro/<id>/` directory holds the minimal test sources, what they show, expected against actual, and a recipe: JDK 25, a Lucene checkout at the tested commit built with `./gradlew --no-daemon assemble`, and the JUnit Platform console launcher, whose vintage engine runs `LuceneTestCase` tests. A failing test is the finding reproducing; a rerun with the same seed replays the same random choices.

## How to help

- If you know the code: pick a finding, check it, and say what you found in an issue in [this repository](https://github.com/shbhar/lucene-ai-bug-hunt/issues) ("real", "intended", "duplicate of ...", or a better fix).
- Please do not open issues or pull requests in apache/lucene about these findings unless a Lucene committer agrees one should be filed. Following Lucene's AI policy, upstream issues and pull requests are written by a person, in their own words.
- The 2 withheld rows rated security-sensitive (in scope of Lucene's SECURITY.md) go privately to security@lucene.apache.org first.
- If you believe any finding has security implications, report it privately to security@lucene.apache.org rather than in a public issue.

## Layout

| Path | Content |
|---|---|
| `index.html` | the findings table and the method notes |
| `bugs/<id>.html`, `assets/<id>/` | public finding pages and their diagrams |
| `repro/<id>/` | minimal repro sources, README and run recipe |
| `models/<unit>/` | formal models and scripts of the hunt units that worked on a public finding |
| `data/bugs.csv`, `data/bugs.json` | the table as data (public fields only) |
| `HOWTO.md`, `howto.html` | how to run a similar hunt in your project |

## License

Apache License 2.0 (see `LICENSE`). Apache Lucene and Apache are trademarks of The Apache Software Foundation; this project is independent and not endorsed by it. See `NOTICE`.
