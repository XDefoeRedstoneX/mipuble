package com.mipuble.domain.sort

/**
 * Compares strings the way a human shelves books: digit runs are compared by
 * numeric value instead of character-by-character, so "Vol 2" sorts before
 * "Vol 10" (plain lexicographic order puts "Vol 10" first because '1' < '2').
 *
 * Properties:
 * - Case-insensitive for text runs.
 * - Digit runs of any length (no overflow — compared by trimmed length, then digits).
 * - Leading zeros don't change a number's value; they only break exact ties,
 *   so "Vol 01" and "Vol 1" stay distinct but adjacent.
 */
class NaturalOrderComparator : Comparator<String> {

    override fun compare(left: String, right: String): Int {
        var i = 0
        var j = 0
        // Remembers the first leading-zero difference; used only if everything
        // else compares equal, so "01" vs "1" is deterministic but not disruptive.
        var zeroTie = 0

        while (i < left.length && j < right.length) {
            val a = left[i]
            val b = right[j]

            if (a.isDigit() && b.isDigit()) {
                val startA = i
                val startB = j
                while (i < left.length && left[i].isDigit()) i++
                while (j < right.length && right[j].isDigit()) j++

                val numA = left.substring(startA, i).trimStart('0')
                val numB = right.substring(startB, j).trimStart('0')

                // A longer digit run (after dropping leading zeros) is a bigger number.
                if (numA.length != numB.length) return numA.length - numB.length
                val byDigits = numA.compareTo(numB)
                if (byDigits != 0) return byDigits

                if (zeroTie == 0) {
                    // Same numeric value; fewer leading zeros sorts first on a full tie.
                    zeroTie = (i - startA) - (j - startB)
                }
            } else {
                val byChar = a.lowercaseChar().compareTo(b.lowercaseChar())
                if (byChar != 0) return byChar
                i++
                j++
            }
        }

        val byRemaining = (left.length - i) - (right.length - j)
        if (byRemaining != 0) return byRemaining
        if (zeroTie != 0) return zeroTie
        // Final tiebreak so compare() agrees with equals() for distinct casings.
        return left.compareTo(right)
    }
}
