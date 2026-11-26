package dev.gzclp.tracker.form

import org.junit.After
import org.junit.Before

/** Every check in [FormTest] again, to its same limits, with the figure drawn and solved as the one [Figure] mesh. */
class FigureFormTest : FormTest() {
    @Before fun mesh() { Figure.on = true }
    @After fun pieces() { Figure.on = false }
}
