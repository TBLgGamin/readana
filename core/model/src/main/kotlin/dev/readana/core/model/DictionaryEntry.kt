package dev.readana.core.model

/** One sense of a word: what part of speech it is, what it means, and an example if there is one. */
data class DictionarySense(val partOfSpeech: String, val definition: String, val example: String?)

/**
 * What the dictionary knows about a word.
 *
 * Senses only — no etymology, no pronunciation. A reader who stopped mid-sentence wants the meaning
 * and their place back, not an article.
 */
data class DictionaryEntry(val word: String, val senses: List<DictionarySense>) {
    val isEmpty: Boolean get() = senses.isEmpty()
}
