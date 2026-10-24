import shlex
import unittest

from collect_metadata import attempt, channel_command, parse_channels, parse_notification, parse_sessions, sessions_command, target_keys

PKG = "com.easyswitchcontroller"


class MetadataParsingTest(unittest.TestCase):
    def test_counts_only_the_target_session_including_blank_watch(self):
        raw = """WINDOW MANAGER SESSIONS
  Session Session{target 123:u0a1}:
    numWindow=2 mAlertWindows={Window{abc u0 EasySwitchFloatingWatch}, Window{def u0 EasySwitchFloatingButton}} mClientDead=false    mPackageName=com.easyswitchcontroller
  Session Session{other 345:u0a2}:
    numWindow=1 mAlertWindows={Window{ghi u0 Other}} mClientDead=false    mPackageName=com.easyswitchcontroller.inputprobe
"""
        parsed = parse_sessions(raw, PKG)
        self.assertEqual(len(parsed), 1)
        self.assertEqual(parsed[0]["alertWindowCount"], 2)

    def test_zero_windows_is_distinct_from_an_unrecognized_format(self):
        raw = "Session Session{target 123:u0a1}:\n mAlertWindows={} mClientDead=false mPackageName=" + PKG
        self.assertEqual(parse_sessions(raw, PKG)[0]["alertWindowCount"], 0)
        with self.assertRaises(ValueError):
            parse_sessions("Permission denied", PKG)

    def test_only_exact_package_and_its_system_notification_are_selected(self):
        own = f"0|{PKG}|1|null|10382"
        system = f"0|android|0|com.android.server.wm.AlertWindowNotification - {PKG}|1000"
        other = system.replace(PKG, PKG + ".inputprobe")
        self.assertEqual([x["key"] for x in target_keys("\n".join([own, system, other]), PKG)], [own, system])
        args = ["cmd", "notification", "get", system]
        self.assertEqual(shlex.split(shlex.join(args)), args)

    def test_notification_text_is_discarded_and_missing_record_is_unknown(self):
        raw = "  mImportance=MIN\n  mCreationTimeMs=123\n  android.text=private\n  mUpdateTimeMs=456"
        self.assertEqual(parse_notification(raw), {"mImportance": "MIN", "mCreationTimeMs": "123", "mUpdateTimeMs": "456"})
        with self.assertRaises(ValueError):
            parse_notification("notification not found")

    def test_empty_or_copy_only_channel_output_is_unknown(self):
        for raw in ("", "TARGET_CHANNEL_FIELDS_V1\n", "TARGET_CHANNEL_FIELDS_V1\nchannel source=active_notification_copy mImportance=1 mDeleted=false mUserLockedFields=0\n"):
            result = attempt(lambda raw=raw: parse_channels(raw))
            self.assertFalse(result["ok"])
            self.assertEqual(result["state"], "unknown")

    def test_only_complete_settings_rows_are_current_settings(self):
        raw = """TARGET_CHANNEL_FIELDS_V1
channel source=active_notification_copy mImportance=1 mUserLockedFields=0 mDeleted=false
channel source=settings_dump mImportance=0 mUserLockedFields=4 mDeleted=true mOriginalImp=1
"""
        self.assertEqual(parse_channels(raw), [{"source": "settings_dump", "mImportance": "0", "mUserLockedFields": "4", "mDeleted": "true", "mOriginalImp": "1"}])
        with self.assertRaises(ValueError):
            parse_channels("TARGET_CHANNEL_FIELDS_V1\nchannel source=settings_dump mImportance=1\n")
        with self.assertRaises(ValueError):
            parse_channels(raw + "channel source=settings_dump mImportance=1 mUserLockedFields=0 mDeleted=false\n")

    def test_failed_read_has_unknown_state_and_measured_interval(self):
        result = attempt(lambda: (_ for _ in ()).throw(RuntimeError("unavailable")))
        self.assertEqual(result["state"], "unknown")
        self.assertIn("at", result)
        self.assertIn("completedAt", result)
        self.assertGreaterEqual(result["durationMs"], 0)

    def test_notification_list_error_is_not_reported_as_zero_notifications(self):
        with self.assertRaises(ValueError):
            target_keys("Permission denial", PKG)
        self.assertEqual(target_keys("", PKG), [])

    def test_channel_command_quotes_package_and_emits_only_whitelisted_fields(self):
        source = channel_command(PKG)
        self.assertIn("awk -v", source)
        self.assertIn("TARGET_CHANNEL_FIELDS_V1", source)
        self.assertIn("mUserLockedFields", source)
        self.assertNotIn("print $0", source)
        self.assertIn("set -o pipefail", source)
        self.assertNotIn("$(dumpsys notification)", source)

    def test_window_sessions_are_filtered_on_the_device(self):
        source = sessions_command(PKG)
        self.assertIn("dumpsys window sessions | awk -v", source)
        self.assertIn("mPackageName=", source)
        self.assertIn("[[:space:]]", source)
        self.assertIn("if(!header) exit 66", source)


if __name__ == "__main__":
    unittest.main()
