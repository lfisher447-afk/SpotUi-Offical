package com.music.spotui.translator

import java.util.Locale

data class LexiconMatch(
    val detectedLangCode: String,
    val englishMeaning: String,
)

/**
 * Ultra-fast (<1ms) offline tactical FPS / Battle Royale / MOBA squad voice callout dictionary.
 * Covers common Call of Duty: Mobile, PUBG Mobile, Valorant, and Free Fire voice chat callouts
 * across Spanish, Russian, Arabic, French, Portuguese, German, Japanese, Korean, Chinese, and Turkish.
 */
object GamingCalloutLexicon {
    private val exactEntries: Map<String, LexiconMatch> = mapOf(
        // Spanish
        "enemigo" to LexiconMatch("es", "Enemy"),
        "enemigo aqui" to LexiconMatch("es", "Enemy here"),
        "arriba" to LexiconMatch("es", "Above / Upstairs"),
        "abajo" to LexiconMatch("es", "Below / Downstairs"),
        "izquierda" to LexiconMatch("es", "Left"),
        "derecha" to LexiconMatch("es", "Right"),
        "atras" to LexiconMatch("es", "Behind us"),
        "atrás" to LexiconMatch("es", "Behind us"),
        "recargando" to LexiconMatch("es", "Reloading"),
        "francotirador" to LexiconMatch("es", "Sniper!"),
        "granada" to LexiconMatch("es", "Grenade!"),
        "cuidado" to LexiconMatch("es", "Watch out!"),
        "ayuda" to LexiconMatch("es", "Help me!"),
        "ayudame" to LexiconMatch("es", "Help me!"),
        "ayúdame" to LexiconMatch("es", "Help me!"),
        "reviveme" to LexiconMatch("es", "Revive me"),
        "revíveme" to LexiconMatch("es", "Revive me"),
        "uno menos" to LexiconMatch("es", "One down"),
        "tumbado" to LexiconMatch("es", "Knocked one"),
        "poca vida" to LexiconMatch("es", "One-shot / Low HP"),
        "vamos" to LexiconMatch("es", "Let's push!"),
        "hola amigos" to LexiconMatch("es", "Hello friends"),
        // Russian
        "справа" to LexiconMatch("ru", "On the right"),
        "слева" to LexiconMatch("ru", "On the left"),
        "сзади" to LexiconMatch("ru", "Behind us!"),
        "враг" to LexiconMatch("ru", "Enemy"),
        "снайпер" to LexiconMatch("ru", "Sniper!"),
        "граната" to LexiconMatch("ru", "Grenade!"),
        "перезарядка" to LexiconMatch("ru", "Reloading"),
        "помоги" to LexiconMatch("ru", "Help me!"),
        "осторожно" to LexiconMatch("ru", "Watch out!"),
        "пуш" to LexiconMatch("ru", "Push now!"),
        "рашим" to LexiconMatch("ru", "Rush in!"),
        "минус один" to LexiconMatch("ru", "One down"),
        // Arabic
        "يلا" to LexiconMatch("ar", "Let's go!"),
        "يمين" to LexiconMatch("ar", "Right"),
        "يسار" to LexiconMatch("ar", "Left"),
        "ورا" to LexiconMatch("ar", "Behind you!"),
        "فوق" to LexiconMatch("ar", "Above / Roof"),
        "تحت" to LexiconMatch("ar", "Below"),
        "قناص" to LexiconMatch("ar", "Sniper!"),
        "انتبه" to LexiconMatch("ar", "Watch out!"),
        "ساعدني" to LexiconMatch("ar", "Help me!"),
        "واحد طاح" to LexiconMatch("ar", "Knocked one"),
        // French
        "à gauche" to LexiconMatch("fr", "Left"),
        "a gauche" to LexiconMatch("fr", "Left"),
        "à droite" to LexiconMatch("fr", "Right"),
        "a droite" to LexiconMatch("fr", "Right"),
        "derrière" to LexiconMatch("fr", "Behind us!"),
        "derriere" to LexiconMatch("fr", "Behind us!"),
        "sniper" to LexiconMatch("fr", "Sniper!"),
        "je recharge" to LexiconMatch("fr", "Reloading"),
        "aidez moi" to LexiconMatch("fr", "Help me!"),
        "aidez-moi" to LexiconMatch("fr", "Help me!"),
        // Portuguese
        "esquerda" to LexiconMatch("pt", "Left"),
        "direita" to LexiconMatch("pt", "Right"),
        "atrás" to LexiconMatch("pt", "Behind us"),
        "inimigo" to LexiconMatch("pt", "Enemy"),
        "recarregando" to LexiconMatch("pt", "Reloading"),
        "me ajuda" to LexiconMatch("pt", "Help me!"),
        "deitei um" to LexiconMatch("pt", "Knocked one"),
        // German
        "links" to LexiconMatch("de", "Left"),
        "rechts" to LexiconMatch("de", "Right"),
        "hinter uns" to LexiconMatch("de", "Behind us!"),
        "achtung" to LexiconMatch("de", "Watch out!"),
        "nachladen" to LexiconMatch("de", "Reloading"),
        // Japanese / Korean / Chinese / Turkish
        "助けて" to LexiconMatch("ja", "Help me!"),
        "後ろ" to LexiconMatch("ja", "Behind us!"),
        "右" to LexiconMatch("ja", "Right"),
        "左" to LexiconMatch("ja", "Left"),
        "도와줘" to LexiconMatch("ko", "Help me!"),
        "뒤에" to LexiconMatch("ko", "Behind us!"),
        "右边" to LexiconMatch("zh", "Right"),
        "左边" to LexiconMatch("zh", "Left"),
        "后面" to LexiconMatch("zh", "Behind us!"),
        "arkamda" to LexiconMatch("tr", "Behind me!"),
        "dikkat" to LexiconMatch("tr", "Watch out!"),
        "yardım" to LexiconMatch("tr", "Help me!"),
    )

    private val profanityTokens = listOf("fuck", "shit", "bitch", "puta", "mierda", "blyat", "cyka", "kurwa")

    fun lookupExact(text: String, sourceHint: String = "auto"): LexiconMatch? {
        val normalized = text.trim()
            .lowercase(Locale.US)
            .replace(Regex("[!?.,。]+"), "")
            .trim()
        if (normalized.isEmpty()) return null
        val match = exactEntries[normalized] ?: return null
        if (sourceHint != "auto" && !sourceHint.equals(match.detectedLangCode, ignoreCase = true)) {
            return match.copy(detectedLangCode = sourceHint.lowercase(Locale.US))
        }
        return match
    }

    fun applyProfanityMaskIfEnabled(text: String, maskEnabled: Boolean): String {
        if (!maskEnabled) return text
        var result = text
        for (bad in profanityTokens) {
            val regex = Regex("\\b${Regex.escape(bad)}\\b", RegexOption.IGNORE_CASE)
            result = result.replace(regex) { m ->
                val v = m.value
                if (v.length <= 2) "**" else "${v.first()}${"*".repeat(v.length - 2)}${v.last()}"
            }
        }
        return result
    }
}
