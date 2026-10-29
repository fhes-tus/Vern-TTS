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
broadcast_spec = importlib.util.spec_from_file_location('vern_broadcast', Path(__file__).with_name('broadcast_release.py'))
broadcast = importlib.util.module_from_spec(broadcast_spec)
broadcast_spec.loader.exec_module(broadcast)

class BotSecurityTest(unittest.TestCase):
    def test_current_release_and_menu_do_not_require_github(self):
        with patch.object(bot, 'get_latest_release', side_effect=AssertionError('Unexpected GitHub lookup')):
            text, menu = bot.get_welcome_content()
            self.assertIn('2.7.0', text)
            self.assertIn('Build 45', text)
            self.assertEqual(menu['inline_keyboard'][0][0]['url'], bot.CONTENT['play_url'])
            self.assertIn('Build 45', bot.get_changelog_content()[0])

    def test_downloads_prioritize_play_and_label_separate_github_release(self):
        release = {'tag':'v2.6.2', 'universal_url':bot.GITHUB_URL+'/releases/latest', 'arm64_url':bot.GITHUB_URL+'/releases/latest'}
        with patch.object(bot, 'get_latest_release', return_value=release):
            text, menu = bot.get_download_content()
            self.assertIn('Build 45', text)
            self.assertIn('Latest GitHub APK release: <b>v2.6.2</b>', text)
            self.assertEqual(menu['inline_keyboard'][0][0]['url'], bot.CONTENT['play_url'])
            self.assertNotIn('review is currently', text)

    def test_github_outage_never_invents_an_apk(self):
        with patch.object(bot, '_cached_release', None), patch.object(bot.urllib.request, 'urlopen', side_effect=OSError('offline')):
            release=bot.get_latest_release()
            for key in ['arm64_url','universal_url','arm32_url']:
                self.assertEqual(release[key],bot.GITHUB_URL+'/releases/latest')

    def test_shared_content_fits_telegram_limits(self):
        self.assertLessEqual(len(bot.CONTENT['bot_description']),512)
        self.assertLessEqual(len(bot.CONTENT['bot_short_description']),120)
        self.assertLessEqual(len(bot.CONTENT['channel_description']),255)
        self.assertLessEqual(len(bot.CONTENT['channel_post']),4096)
        for command in bot.CONTENT['commands']:
            self.assertLessEqual(len(command['description']),256)

    def test_channel_announcement_rejects_unpublished_and_never_invents_assets(self):
        for data in [None, {'tag_name':'v2.7.0','draft':True}, {'tag_name':'v2.7.0','prerelease':True}]:
            with self.assertRaises(ValueError):
                broadcast.build_announcement(data)
        text, markup = broadcast.build_announcement({'tag_name':'v2.7.0','assets':[]})
        self.assertIn('Build 45', text)
        self.assertEqual(markup['inline_keyboard'][0][0]['url'], bot.CONTENT['play_url'])
        self.assertNotIn('.apk', json.dumps(markup))

    def test_future_channel_announcement_escapes_content_and_uses_real_assets(self):
        url=bot.GITHUB_URL+'/releases/download/v9/Vern-v9-universal.apk'
        text, markup = broadcast.build_announcement({'tag_name':'v9', 'body':'- Read <safely> & enjoy', 'assets':[
            {'name':'Vern-v9-universal.apk','browser_download_url':url},
            {'name':'universal-mapping.zip','browser_download_url':bot.GITHUB_URL+'/releases/download/v9/mapping.zip'}]})
        self.assertIn('&lt;safely&gt; &amp; enjoy', text)
        self.assertIn(url,json.dumps(markup))
        self.assertNotIn('mapping.zip',json.dumps(markup))

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
