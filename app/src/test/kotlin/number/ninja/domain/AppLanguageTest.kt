package number.ninja.domain

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AppLanguageTest {

    @Test
    fun `empty locale list means same as system`() {
        assertNull(AppLanguage.fromLanguageTags(""))
    }

    @Test
    fun `resolves supported regional and fallback locale tags`() {
        assertEquals(AppLanguage.UKRAINIAN, AppLanguage.fromLanguageTags("uk-UA"))
        assertEquals(AppLanguage.RUSSIAN, AppLanguage.fromLanguageTags("ru,en-US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromLanguageTags("en-GB"))
    }

    @Test
    fun `unsupported app locale is not mistaken for an explicit supported language`() {
        assertNull(AppLanguage.fromLanguageTags("fr-FR"))
    }
}
