package com.music.spotui.translator

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class MultiEngineTranslationRouterAndLexiconTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun gamingLexiconDetectsAndTranslatesMultilingualCalloutsAndMasksProfanity() {
        val es = GamingCalloutLexicon.lookupExact("¡Revíveme!", "auto")
        assertNotNull(es)
        assertEquals("es", es?.detectedLangCode)
        assertEquals("Revive me", es?.englishMeaning)

        val ru = GamingCalloutLexicon.lookupExact("Снайпер!", "auto")
        assertNotNull(ru)
        assertEquals("ru", ru?.detectedLangCode)

        val masked = GamingCalloutLexicon.applyProfanityMaskIfEnabled("Watch out blyat behind us", maskEnabled = true)
        assertTrue(masked.contains("b***t"))
        assertFalse(masked.contains("blyat"))
    }

    @Test
    fun routerCascadeOrderPrioritizesLexiconThenOfflineThenWeb() {
        val order = MultiEngineTranslationRouter.resolveCascadeOrder(
            selected = TranslationEngineBackend.HYBRID_CASCADE,
            gamingLexiconEnabled = true,
        )
        assertEquals(TranslationEngineBackend.GAMING_LEXICON, order.first())
        assertTrue(TranslationEngineBackend.MLKIT_OFFLINE in order)
        assertTrue(TranslationEngineBackend.LINGVA_MIRROR in order)
        assertTrue(TranslationEngineBackend.LIBRE_TRANSLATE in order)
        assertTrue(TranslationEngineBackend.MYMEMORY_API in order)
        assertTrue(TranslationEngineBackend.GOOGLE_GTX in order)
    }

    @Test
    fun jsonParsersExtractTranslationsCleanly() {
        assertEquals("Need ammo", MultiEngineTranslationRouter.parseLingvaJson("""{"translation":"Need ammo"}"""))
        assertEquals("Enemy left", MultiEngineTranslationRouter.parseLibreJson("""{"translatedText":"Enemy left"}"""))
        assertEquals("Push mid", MultiEngineTranslationRouter.parseMyMemoryJson("""{"responseData":{"translatedText":"Push mid"}}"""))
        assertEquals(
            "Watch out behind us!",
            MultiEngineTranslationRouter.parseGoogleGtxJson("""[[["Watch out ","Cuidado ",null,null],["behind us!","atras!",null,null]]]"""),
        )
        assertNull(MultiEngineTranslationRouter.parseLingvaJson("invalid json"))
    }

    @Test
    fun fourSecondSilenceAutoHideMatchesExactBoundary() {
        assertFalse(GameVoiceTranslatorEngine.shouldHideCaptionAfterSilence(lastSpeechAtMs = 10_000L, nowMs = 13_999L, autoHideSilenceMs = 4_000L))
        assertTrue(GameVoiceTranslatorEngine.shouldHideCaptionAfterSilence(lastSpeechAtMs = 10_000L, nowMs = 14_000L, autoHideSilenceMs = 4_000L))
        assertFalse(GameVoiceTranslatorEngine.shouldHideCaptionAfterSilence(lastSpeechAtMs = 0L, nowMs = 20_000L, autoHideSilenceMs = 4_000L))
    }

    @Test
    fun voskZipExtractionUnwrapsTopFolderAndBlocksZipSlip() {
        val goodZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                listOf(
                    "vosk-model-small-en-us-0.15/am/final.mdl",
                    "vosk-model-small-en-us-0.15/conf/model.conf",
                    "vosk-model-small-en-us-0.15/graph/HCLG.fst",
                ).forEach { entry ->
                    zos.putNextEntry(ZipEntry(entry))
                    zos.write("ok".toByteArray())
                    zos.closeEntry()
                }
            }
        }.toByteArray()

        val staging = tempFolder.newFolder("staging")
        TranslatorModelManager.extractZipSafely(ByteArrayInputStream(goodZip), staging)
        val resolved = TranslatorModelManager.unwrapSingleTopDirectory(staging)
        assertTrue(TranslatorModelManager.isVoskModelValid(resolved))

        val traversalZip = ByteArrayOutputStream().apply {
            ZipOutputStream(this).use { zos ->
                zos.putNextEntry(ZipEntry("../../evil.txt"))
                zos.write("bad".toByteArray())
                zos.closeEntry()
            }
        }.toByteArray()

        val staging2 = tempFolder.newFolder("staging_evil")
        var blocked = false
        try {
            TranslatorModelManager.extractZipSafely(ByteArrayInputStream(traversalZip), staging2)
        } catch (_: SecurityException) {
            blocked = true
        }
        assertTrue("Zip-slip traversal must throw SecurityException", blocked)
        assertFalse(File(staging2.parentFile, "evil.txt").exists())
    }
}
