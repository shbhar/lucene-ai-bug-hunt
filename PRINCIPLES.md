# Some Principles of Large-Scale AI-Assisted Bug Hunting

For the concrete setup and instructions you can give an agent, see [the how-to](howto.html).

## 1. Trust in Output First

Human review and attention is at a premium, so all findings must be thoroughly verified, and we use the following techniques for this:

a. Minimal failing unit tests.

b. Different model family & agent-based blind verification.

c. Adversarial checking if the finding is actually a bug or possibly intended behavior. We do this by seeing from the angles of:

- Javadocs
- Past discussions on related PRs
- GitHub issue discussions
- Code comments
- Dev mailing list discussions
- Previous version behavior (tested, not just with code reading)
- Expected or unexpected changes between versions

d. Also always check if a finding is already known and reported by others, and mark it so.

## 2. The So What

Mature or large code bases will always have bugs: some reachable, others latent, and many contrived, etc. Not every unexpected behavior or bug has to be fixed at the same priority. So make agents reason about the priority/behavior; for contrived bugs, either mark clearly or just drop them.

## 3. The Real Work Is the Fix, and Needs a Lot of Human Input, So We Have to Be Human First

This means the findings have to be presented in a way that makes it easy for humans to take it forward. This is why maintaining a separate [website](index.html)/tracker etc. is more useful than flooding GitHub with AI-generated issues.

- Must always respect a project's AI policy.
- Explainer docs which succinctly explain the issue with diagrams and AI/code-generated videos.
- Minimal unit test (minimal is important: current models often are right, but in a complicated way).
- Adversarially verified and test suggestions for fixing, regression unit tests (also with the lens of elegance/minimalism and not slop).

Such presentation makes it much more efficient for humans to quickly scan the issue and use precious attention for judgment, etc.

## 4. Building for the Next Model & Trusting the Model

In a large-scale and "breadth-first" exercise like this, and especially if the operator has prior expertise, it can be tempting to give very specific prompts, but it can often backfire and unnecessarily limit the model. We experienced this in the Lucene bug hunt, where initially I prompted the model to use formal verification techniques only (hence the bug ids are of the format LFV = Lucene Formal Verification), but as I pored over the output and tried to understand what the model was doing, I saw it was actually coming up with hypotheses directly from reading the code and often then confirming with a formal model. So then I relaxed the prompt and let the model use its judgement on which formal models to use (which is why it used so many) and when/how to use. Only the other principles of trust/verification etc. are needed, and the path to that result is kept flexible. It's likely the case that the next model version will be even more capable, and keeping it outcome-focused like this means the same workflow will just automatically work or easily extend to it, instead of having to rethink the workflow etc.

## 5. Security and Safety

Agents are often very good at finding bugs that may have security implications on the project itself or some downstream consumer, so vet such findings carefully and take the proper channels to avoid causing incidents due to them.
