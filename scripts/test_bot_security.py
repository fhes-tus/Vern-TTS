import importlib.util
import io
import json
from pathlib import Path
import unittest
from unittest.mock import patch
import urllib.error

spec = importlib.util.spec_from_file_location('vern_bot', Path(__file__).with_name('bot_polling.py'))
bot = importlib.util.module_from_spec(spec)
spec.loader.exec_module(bot)

class BotSecurityTest(unittest.TestCase):
    def test_missing_token_fails_without_network(self):
        with patch.object(bot, 'TOKEN', ''), patch.object(bot.urllib.request, 'urlopen') as network:
            with self.assertRaises(RuntimeError): bot.api_call('getMe')
            network.assert_not_called()

    def test_rate_limit_retries_are_bounded_and_sleep_is_capped(self):
        def reject(*args, **kwargs):
            raise urllib.error.HTTPError('https://example.test', 429, 'rate limited', {},
                                         io.BytesIO(b'{"parameters":{"retry_after":9999}}'))
        with patch.object(bot, 'TOKEN', 'synthetic'), patch.object(bot.urllib.request, 'urlopen', side_effect=reject) as network, patch.object(bot.time, 'sleep') as sleep:
            self.assertIsNone(bot.api_call('getUpdates'))
            self.assertEqual(network.call_count, 3)
            self.assertEqual([call.args[0] for call in sleep.call_args_list], [60, 60])

    def test_release_downloads_use_actual_apk_assets(self):
        data = {'tag_name': 'v9.0', 'assets': [
            {'name': 'Vern-v9.0-arm64.apk', 'browser_download_url': 'https://github.com/fhes-tus/Vern-TTS/releases/download/v9.0/Vern-v9.0-arm64.apk'},
            {'name': 'arm64-symbols.zip', 'browser_download_url': 'https://github.com/fhes-tus/Vern-TTS/releases/download/v9.0/arm64-symbols.zip'}]}
        with patch.object(bot, '_cached_release', None), patch.object(bot.urllib.request, 'urlopen', return_value=io.BytesIO(json.dumps(data).encode())):
            release = bot.get_latest_release()
            self.assertTrue(release['arm64_url'].endswith('/Vern-v9.0-arm64.apk'))
            self.assertEqual(release['universal_url'], bot.GITHUB_URL + '/releases/latest')

if __name__ == '__main__': unittest.main()
