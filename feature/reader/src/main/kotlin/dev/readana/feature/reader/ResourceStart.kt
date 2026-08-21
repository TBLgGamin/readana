package dev.readana.feature.reader

import org.readium.r2.shared.publication.Locator

/** Past this much of a resource, reading on can only mean crossing into the next one. */
private const val END_OF_RESOURCE = 0.95

/** Anything above this is far enough into a resource to look like a carried-over position. */
private const val START_OF_RESOURCE = 0.01

/**
 * Whether landing on [current] should be corrected to the start of that resource.
 *
 * In scrolling mode Readium carries the scroll offset across a chapter boundary, so reading to the
 * end of one chapter drops you the same distance into the next one instead of at its first line.
 *
 * Only a boundary reached by *reading forward* is corrected — the previous position has to have
 * been at the very end of its resource. A jump from the contents list, a bookmark or a search hit
 * starts from somewhere else in the book and is meant to land where it lands.
 */
internal fun shouldSnapToResourceStart(previous: Locator?, current: Locator, scrolling: Boolean): Boolean {
    if (!scrolling || previous == null) return false
    if (previous.href == current.href) return false
    val readToTheEnd = (previous.locations.progression ?: 0.0) >= END_OF_RESOURCE
    val landedPastTheStart = (current.locations.progression ?: 0.0) > START_OF_RESOURCE
    return readToTheEnd && landedPastTheStart
}

/** The same resource, at its very beginning. */
internal fun Locator.atResourceStart(): Locator = copy(
    locations = Locator.Locations(progression = 0.0),
    text = Locator.Text()
)
