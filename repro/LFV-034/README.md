# LFV-034: withdrawn

Withdrawn after review: only an invalid dimension is affected; it fails at once with an IndexOutOfBoundsException subtype, as the sibling *Range.getMin does through Objects.checkIndex, and every valid dimension returns the right value.
