package com.karapp.drawguidance.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.karapp.drawguidance.R
import com.karapp.drawguidance.ui.components.MascotFace
import com.karapp.drawguidance.ui.components.MascotFigure
import com.karapp.drawguidance.ui.components.MascotGesture
import com.karapp.drawguidance.ui.components.MascotLook
import com.karapp.drawguidance.ui.i18n.Text
import com.karapp.drawguidance.ui.theme.NeuTheme

/** v39 Ana sayfa kartlarının kararları (saf; testlenir). */
object HomeV39 {
    /** Premium öneri kartı nereye götürür. */
    enum class PremiumTarget { SIGN_IN, SUBSCRIPTION }

    fun premiumTarget(signedIn: Boolean): PremiumTarget = if (signedIn) PremiumTarget.SUBSCRIPTION else PremiumTarget.SIGN_IN

    /** Premium kullanıcıya kart gösterilmez. */
    fun showPremiumCard(isPremium: Boolean): Boolean = !isPremium

    /** Görev kartı: giriş yapmış 13 yaşından küçük kullanıcı. */
    fun showChallengeCard(signedIn: Boolean, birthDate: String?): Boolean =
        signedIn && com.karapp.drawguidance.data.settings.KidsPrefs.isUnder(birthDate, com.karapp.drawguidance.data.settings.KidsPrefs.CHALLENGE_AGE)
}

/** v40 Ana sayfadaki görev kartının önerisi. */
enum class ChallengeHomeAction {
    /** Hiç seviye geçilmedi: göreve başla. */
    START,
    /** Kaldığı seviyeden devam. */
    CONTINUE,
    /** Sıradaki seviye Premium ve hak yok: Premium'a bak ya da bir seviyeyi yeniden oyna. */
    PREMIUM,
    /** Bütün seviyeler geçildi: bir seviyeyi yeniden oyna. */
    ALL_DONE,
}

/**
 * v40 görev kartının içeriği: [action], geçilen seviye sayısı, açılacak seviyenin sırası ([level], 0'dan; PREMIUM ve
 * ALL_DONE'da yeniden oynanacak seviye), seviye sayısı ve Free plandaki seviye sayısı.
 */
data class ChallengeHomePlan(val action: ChallengeHomeAction, val passed: Int, val level: Int, val levelCount: Int, val freeLevels: Int) {
    companion object {
        /**
         * En iyi puanlardan ([best]: seviye sırası → puan) karar: geçilmemiş ilk seviye Free'deyse (ya da Premium hakkı
         * varsa) devam; Premium'sa ve hak yoksa Premium önerisi ve Free seviyelerden en az yıldızlısının (eşitse ilkinin)
         * yeniden oynanması; hepsi geçildiyse en az yıldızlı seviyenin yeniden oynanması.
         */
        fun of(best: Map<Int, Int>, levelCount: Int, freeLevels: Int, pass: Int, premium: Boolean, stars: (Int) -> Int): ChallengeHomePlan {
            val inRange = best.filterKeys { it in 0 until levelCount }
            val passed = com.karapp.drawguidance.data.challenge.ChallengeProgressRepository.passedCount(inRange, pass)
            val next = com.karapp.drawguidance.data.challenge.ChallengeProgressRepository.nextLevel(inRange, levelCount, pass)
            fun replay(range: IntRange) = range.minByOrNull { stars(inRange[it] ?: 0) } ?: 0
            return when {
                levelCount > 0 && (0 until levelCount).all { (inRange[it] ?: -1) >= pass } ->
                    ChallengeHomePlan(ChallengeHomeAction.ALL_DONE, passed, replay(0 until if (premium) levelCount else minOf(freeLevels, levelCount)), levelCount, freeLevels)
                next >= freeLevels && !premium ->
                    ChallengeHomePlan(ChallengeHomeAction.PREMIUM, passed, replay(0 until minOf(freeLevels, levelCount)), levelCount, freeLevels)
                passed == 0 -> ChallengeHomePlan(ChallengeHomeAction.START, 0, next, levelCount, freeLevels)
                else -> ChallengeHomePlan(ChallengeHomeAction.CONTINUE, passed, next, levelCount, freeLevels)
            }
        }
    }
}

/**
 * v39 Ana sayfada 13 yaşından küçüklere çizim görevi kartı: maskot Boyacık ve hiç seviye geçilmediyse göreve başlama
 * önerisi ("Devam et"), geçildiyse "Kaldığın seviyeden devam et (N)". [nextLevel] açılacak seviyenin numarası (1'den).
 */
@Composable
fun ChallengeHomeCard(passed: Int, nextLevel: Int, onContinue: () -> Unit, modifier: Modifier = Modifier) {
    val count = com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels.count
    val plan = ChallengeHomePlan(
        if (passed > 0) ChallengeHomeAction.CONTINUE else ChallengeHomeAction.START, passed, nextLevel - 1, count,
        com.karapp.drawguidance.ui.screens.canvasedit.challenge.ChallengeLevels.FREE_LEVELS,
    )
    ChallengeHomeCard(plan, onOpen = { onContinue() }, onPremium = {}, modifier = modifier)
}

/**
 * v40 görev kartı ([plan]'a göre): başla / "Kaldığın seviyeden devam et (N)" / sıradaki seviye Premium'sa "Premium'a
 * bak" ve "Tekrar oyna (N)" / hepsi geçildiyse "Tekrar oyna (N)". [onOpen] seviyeyi (0'dan) açar.
 */
@Composable
fun ChallengeHomeCard(plan: ChallengeHomePlan, onOpen: (Int) -> Unit, onPremium: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val card = RoundedCornerShape(28.dp)
    val ink = if (colors.isDark) Color(0xFFF5F3FF) else Color(0xFF2B2140)
    val passed = plan.passed
    Box(
        modifier.fillMaxWidth().clip(card)
            .background(
                Brush.linearGradient(
                    if (colors.isDark) listOf(Color(0xFF2A3B5A), Color(0xFF3B2E5A)) else listOf(Color(0xFFDDF4FF), Color(0xFFE3DAFF), Color(0xFFFFF1C9)),
                ),
            )
            .border(2.dp, Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFFA78BFA))), card)
            .testTag("home_challenge_card"),
    ) {
        Column(Modifier.padding(start = 10.dp, end = 16.dp, top = 14.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MascotFigure(
                    MascotLook(
                        when (plan.action) {
                            ChallengeHomeAction.START -> MascotFace.EXCITED
                            ChallengeHomeAction.ALL_DONE -> MascotFace.PROUD
                            else -> MascotFace.HAPPY
                        },
                        if (plan.action == ChallengeHomeAction.ALL_DONE) MascotGesture.CHEER else MascotGesture.WAVE,
                    ),
                    Modifier.size(100.dp).testTag("home_challenge_mascot"),
                )
                Column(Modifier.weight(1f).padding(start = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.v39_challenge_title), style = MaterialTheme.typography.labelLarge, color = Color(0xFF8B5CF6), fontWeight = FontWeight.Bold)
                    Text(
                        when (plan.action) {
                            ChallengeHomeAction.START -> stringResource(R.string.v39_challenge_body_new)
                            ChallengeHomeAction.CONTINUE -> stringResource(R.string.v40_challenge_body_resume, passed, plan.levelCount)
                            ChallengeHomeAction.PREMIUM -> stringResource(R.string.v40_challenge_body_premium, plan.freeLevels, plan.levelCount - plan.freeLevels)
                            ChallengeHomeAction.ALL_DONE -> stringResource(R.string.v40_challenge_body_all_done, plan.levelCount)
                        },
                        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = ink,
                        modifier = Modifier.testTag("home_challenge_text"),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Spacer(Modifier.weight(1f))
                when (plan.action) {
                    ChallengeHomeAction.START -> HomePill(stringResource(R.string.v39_challenge_continue), Icons.Filled.PlayArrow, green = true, "home_challenge_continue") { onOpen(plan.level) }
                    ChallengeHomeAction.CONTINUE -> HomePill(stringResource(R.string.v39_challenge_resume, plan.level + 1), Icons.Filled.PlayArrow, green = true, "home_challenge_continue") { onOpen(plan.level) }
                    ChallengeHomeAction.PREMIUM -> {
                        HomePill(stringResource(R.string.v40_challenge_replay, plan.level + 1), Icons.Filled.Replay, green = false, "home_challenge_replay") { onOpen(plan.level) }
                        HomePill(stringResource(R.string.v40_challenge_go_premium), Icons.Filled.WorkspacePremium, green = true, "home_challenge_premium", onPremium)
                    }
                    ChallengeHomeAction.ALL_DONE -> HomePill(stringResource(R.string.v40_challenge_replay, plan.level + 1), Icons.Filled.Replay, green = true, "home_challenge_replay") { onOpen(plan.level) }
                }
            }
        }
    }
}

/** v40 görev kartının hap düğmesi (yeşil: ana iş; sade: ikincil). */
@Composable
private fun HomePill(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, green: Boolean, tag: String, onClick: () -> Unit) {
    val ink = if (NeuTheme.colors.isDark) Color(0xFFF5F3FF) else Color(0xFF2B2140)
    Row(
        Modifier.clip(CircleShape)
            .then(
                if (green) Modifier.background(Brush.verticalGradient(listOf(Color(0xFF4ADE80), Color(0xFF16A34A))))
                else Modifier.background(Color.White.copy(alpha = if (NeuTheme.colors.isDark) 0.08f else 0.75f)),
            )
            .border(2.dp, if (green) Color.White.copy(alpha = 0.85f) else Color(0xFFA78BFA), CircleShape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = if (green) Color.White else ink, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.titleSmall, color = if (green) Color.White else ink, fontWeight = FontWeight.Bold)
    }
}

/** v39 Ana sayfanın altındaki Premium önerisi (Premium kullanıcıda gösterilmez). */
@Composable
fun PremiumHomeCard(signedIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = NeuTheme.colors
    val card = RoundedCornerShape(24.dp)
    Row(
        modifier.fillMaxWidth().clip(card)
            .background(Brush.linearGradient(listOf(colors.gradientDeep, colors.gradientEnd)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(18.dp)
            .testTag("home_premium_card"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(Icons.Filled.WorkspacePremium, null, tint = com.karapp.drawguidance.ui.premium.Gold, modifier = Modifier.size(40.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(R.string.v39_home_premium_title), style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.v39_home_premium_body), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
        }
        Text(
            stringResource(if (signedIn) R.string.v39_home_premium_cta else R.string.v39_home_premium_sign_in),
            style = MaterialTheme.typography.labelLarge, color = com.karapp.drawguidance.ui.premium.Gold, fontWeight = FontWeight.Bold,
            modifier = Modifier.testTag("home_premium_cta"),
        )
    }
}
