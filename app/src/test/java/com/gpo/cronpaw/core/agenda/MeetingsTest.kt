package com.gpo.cronpaw.core.agenda

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeetingsTest {
    @Test fun findsTheCallInTheLocationBeforeTheNotes() {
        assertEquals(
            "https://us02web.zoom.us/j/123456789?pwd=abc",
            Meetings.find("https://us02web.zoom.us/j/123456789?pwd=abc", "Join: https://meet.google.com/abc-defg-hij"),
        )
        assertEquals("https://meet.google.com/abc-defg-hij", Meetings.find("3F 会议室", "Join with Google Meet: https://meet.google.com/abc-defg-hij."))
    }

    @Test fun readsTencentAndTeamsInChineseText() {
        assertEquals("https://meeting.tencent.com/dm/AbCd1234", Meetings.find(null, "点击链接入会：https://meeting.tencent.com/dm/AbCd1234，会议号 123"))
        assertEquals("腾讯会议", Meetings.service("https://meeting.tencent.com/dm/AbCd1234"))
        assertEquals("Teams", Meetings.service("https://teams.microsoft.com/l/meetup-join/19%3a"))
        assertEquals("Zoom", Meetings.service("https://zoom.us/j/1"))
    }

    @Test fun ignoresOrdinaryLinks() {
        assertNull(Meetings.find("https://maps.google.com/?q=Tokyo", "see https://example.com/zoom.us/j/1"))
    }
}
