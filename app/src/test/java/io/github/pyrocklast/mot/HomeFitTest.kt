package io.github.pyrocklast.mot

import io.github.pyrocklast.mot.util.HomeFit
import org.junit.Assert.assertEquals
import org.junit.Test

/** Normal home spacing stays comfortable even when users assign many apps. */
class HomeFitTest {
    @Test fun regularTextKeepsUsualPadding() {
        assertEquals(22, HomeFit.rowPadding(109f, usual = 22, touchTarget = 132))
    }

    @Test fun smallTextMeets48dpTouchTarget() {
        assertEquals(39, HomeFit.rowPadding(54f, usual = 22, touchTarget = 132))
        assertEquals(23, HomeFit.rowPadding(87f, usual = 22, touchTarget = 132))
    }

    @Test fun paddingNeverShrinksWhenMoreAppsAreSelected() {
        assertEquals(22, HomeFit.rowPadding(109f, usual = 22, touchTarget = 132))
    }

    @Test fun tallAccessibleTextDoesNotRequireAdditionalPadding() {
        assertEquals(22, HomeFit.rowPadding(164f, usual = 22, touchTarget = 132))
    }
}
