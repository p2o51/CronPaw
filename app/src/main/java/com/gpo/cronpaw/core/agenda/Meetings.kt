package com.gpo.cronpaw.core.agenda

/**
 * Video-call links in an event's location or notes: Google Meet, Zoom, 腾讯会议 (and VooV),
 * Teams, Webex. The first one found is the meeting; anything else stays plain text.
 */
object Meetings {
    private val link = Regex(
        """https?://(?:meet\.google\.com|(?:[\w-]+\.)?zoom\.(?:us|com\.cn)|meeting\.tencent\.com|voovmeeting\.com|teams\.microsoft\.com|teams\.live\.com|[\w-]+\.webex\.com)/[^\s<>"'）)\]]*""",
        RegexOption.IGNORE_CASE,
    )

    /** The meeting link in [texts], in order (location first, then notes), or null. */
    fun find(vararg texts: String?): String? =
        texts.firstNotNullOfOrNull { t -> t?.let { link.find(it)?.value?.trimEnd('.', ',', '。', '，') } }

    /** The service's name, for the 加入 button. */
    fun service(url: String): String {
        val host = url.substringAfter("://").substringBefore('/').lowercase()
        return when {
            host == "meet.google.com" -> "Google Meet"
            "zoom." in host -> "Zoom"
            "tencent" in host || "voov" in host -> "腾讯会议"
            "teams." in host -> "Teams"
            "webex" in host -> "Webex"
            else -> "会议"
        }
    }
}
