package com.gpo.cronpaw.core.focus

import android.app.AutomaticZenRule
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.service.notification.Condition
import com.gpo.cronpaw.MainActivity
import com.gpo.cronpaw.R

/**
 * CronPaw's own 专注 mode. On Android 15+ it is a mode of its own in 设置 > 模式 (an
 * [AutomaticZenRule]): who may break through is set there, and turning it off never touches the
 * user's own do-not-disturb. Older systems get the interruption filter directly, and the one it
 * replaced is put back afterwards.
 */
object FocusZen {
    private val conditionId: Uri = Uri.parse("cronpaw://focus")
    private const val RuleKey = "zen_rule_id"
    private const val ActiveKey = "zen_active"
    private const val PrevFilterKey = "zen_prev_filter"

    private fun nm(context: Context) = context.getSystemService(NotificationManager::class.java)

    fun hasAccess(context: Context): Boolean = runCatching { nm(context).isNotificationPolicyAccessGranted }.getOrDefault(false)

    fun accessIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    /** The mode's own page in system settings (who may interrupt, grayscale…). */
    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < 35 || !hasAccess(context)) return null
        val id = ruleId(context) ?: return null
        return Intent(Settings.ACTION_AUTOMATIC_ZEN_RULE_SETTINGS).putExtra(Settings.EXTRA_AUTOMATIC_ZEN_RULE_ID, id)
    }

    /** Whether the mode should be on for [s] right now. */
    fun wanted(s: FocusSession?): Boolean {
        if (s == null || !s.quiet) return false
        return when (s.phase) {
            Phase.Focus -> true
            Phase.Break -> FocusSettings.quietInBreaks.value
            else -> false
        }
    }

    fun apply(context: Context, on: Boolean) {
        if (!hasAccess(context)) return
        val active = FocusStore.getInt(ActiveKey, 0) == 1
        if (on == active) return
        runCatching {
            if (Build.VERSION.SDK_INT >= 35) {
                val id = ruleId(context) ?: return
                val state = if (on) Condition.STATE_TRUE else Condition.STATE_FALSE
                nm(context).setAutomaticZenRuleState(id, Condition(conditionId, if (on) "专注中" else "", state, Condition.SOURCE_USER_ACTION))
            } else {
                val manager = nm(context)
                if (on) {
                    FocusStore.putInt(PrevFilterKey, manager.currentInterruptionFilter)
                    if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
                        manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                    }
                } else {
                    // Only undo what we did: the user may have changed it meanwhile.
                    val prev = FocusStore.getInt(PrevFilterKey, NotificationManager.INTERRUPTION_FILTER_ALL)
                    if (manager.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_PRIORITY &&
                        prev == NotificationManager.INTERRUPTION_FILTER_ALL
                    ) manager.setInterruptionFilter(prev)
                }
            }
            FocusStore.putInt(ActiveKey, if (on) 1 else 0)
        }
    }

    /** Our rule, made the first time it is needed (or again if the user deleted it). */
    private fun ruleId(context: Context): String? {
        if (Build.VERSION.SDK_INT < 35) return null
        val manager = nm(context)
        FocusStore.getString(RuleKey)?.let { id -> if (runCatching { manager.getAutomaticZenRule(id) }.getOrNull() != null) return id }
        val rule = AutomaticZenRule.Builder("CronPaw 专注", conditionId)
            .setType(AutomaticZenRule.TYPE_OTHER)
            .setConfigurationActivity(ComponentName(context, MainActivity::class.java))
            .setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            .setManualInvocationAllowed(true)
            .setIconResId(R.drawable.ic_focus)
            .setTriggerDescription("在 CronPaw 里开始专注时")
            .build()
        val id = runCatching { manager.addAutomaticZenRule(rule) }.getOrNull() ?: return null
        FocusStore.putString(RuleKey, id)
        return id
    }
}
