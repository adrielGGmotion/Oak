package com.oak.app.data

import oak.composeapp.generated.resources.Res
import oak.composeapp.generated.resources.font_family_system
import oak.composeapp.generated.resources.font_family_inter
import oak.composeapp.generated.resources.font_family_josefin_sans
import oak.composeapp.generated.resources.font_family_lexend_deca
import oak.composeapp.generated.resources.font_family_noto_sans
import oak.composeapp.generated.resources.font_family_plus_jakarta_sans
import oak.composeapp.generated.resources.font_family_lora
import oak.composeapp.generated.resources.font_family_merriweather
import oak.composeapp.generated.resources.font_family_prata
import oak.composeapp.generated.resources.font_family_jetbrains_mono
import org.jetbrains.compose.resources.StringResource

enum class OakFontFamily(val displayNameRes: StringResource) {
    System(Res.string.font_family_system),
    Inter(Res.string.font_family_inter),
    JosefinSans(Res.string.font_family_josefin_sans),
    LexendDeca(Res.string.font_family_lexend_deca),
    NotoSans(Res.string.font_family_noto_sans),
    PlusJakartaSans(Res.string.font_family_plus_jakarta_sans),
    Lora(Res.string.font_family_lora),
    Merriweather(Res.string.font_family_merriweather),
    Prata(Res.string.font_family_prata),
    JetBrainsMono(Res.string.font_family_jetbrains_mono),
}
