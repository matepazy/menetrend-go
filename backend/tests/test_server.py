import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import server


class BackendTests(unittest.TestCase):
    def setUp(self):
        self.client = server.app.test_client()

    def test_coordinates_reject_non_numeric_nonfinite_and_out_of_range(self):
        for value in (None, {'lat': True, 'lon': 19}, {'lat': '47', 'lon': 19},
                      {'lat': float('nan'), 'lon': 19}, {'lat': 91, 'lon': 19}):
            with self.subTest(value=value), self.assertRaises(TypeError):
                server.coordinates(value)

    @patch.object(server, 'graphql')
    def test_bad_plan_never_reaches_upstream(self, upstream):
        for body in ({}, [], {'from': {'lat': 47, 'lon': 19}, 'to': {'lat': 47, 'lon': 18},
                             'dateTime': 'tomorrow'}):
            self.assertEqual(self.client.post('/api/plan', json=body).status_code, 400)
        upstream.assert_not_called()

    @patch.object(server, 'graphql', return_value={'plan': {'itineraries': []}})
    def test_timezone_is_converted_to_budapest(self, upstream):
        response = self.client.post('/api/plan', json={'from': {'lat': 47, 'lon': 19},
            'to': {'lat': 47, 'lon': 18}, 'dateTime': '2026-10-22T12:00:00Z', 'arriveBy': True})
        self.assertEqual(response.status_code, 200)
        self.assertIn('time:"14:00"', upstream.call_args.args[0])
        self.assertIn('arriveBy:true', upstream.call_args.args[0])

    def test_station_search_length_is_bounded(self):
        self.assertEqual(self.client.get('/api/stations?q=a').status_code, 400)
        self.assertEqual(self.client.get('/api/stations?q=' + 'a' * 101).status_code, 400)

    @patch.object(server, 'graphql', return_value={'plan': {'itineraries': [{'legs': [
        {'startTime': 1, 'route': {'shortName': '<span>icon</span>'},
         'trip': {'tripShortName': '900'}, 'from': {'name': 'Origin'}, 'to': {'name': 'Destination'}}]}]}})
    def test_provider_markup_and_placeholder_names_are_normalized(self, upstream):
        response = self.client.post('/api/plan', json={
            'from': {'lat': 47, 'lon': 19, 'name': 'Induló megálló'},
            'to': {'lat': 47, 'lon': 18, 'name': 'Célmegálló'}, 'dateTime': '2026-10-22T12:00'})
        leg = response.json['plan']['itineraries'][0]['legs'][0]
        self.assertEqual(leg['route']['shortName'], '900')
        self.assertEqual(leg['from']['name'], 'Induló megálló')
        self.assertEqual(leg['to']['name'], 'Célmegálló')

    @patch.object(server, 'graphql', return_value={'trip': {'gtfsId': 'safe', 'route': {}}})
    def test_trip_id_is_json_escaped_and_cached(self, upstream):
        server.trip_cache.clear()
        params = {'id': 'trip"} injected', 'date': '2026-10-22'}
        self.assertEqual(self.client.get('/api/trip', query_string=params).status_code, 200)
        self.assertIn('trip\\"} injected', upstream.call_args.args[0])
        self.client.get('/api/trip', query_string=params)
        self.assertEqual(upstream.call_count, 1)

    def test_last_good_snapshot_retains_its_timestamp_and_marks_stale(self):
        previous = server.snapshot
        try:
            server.snapshot = {'lastUpdated': 1, 'vehicles': [{'id': 'old'}]}
            result = self.client.get('/live_data.json')
            self.assertEqual(result.json['lastUpdated'], 1)
            self.assertTrue(result.json['stale'])
            self.assertEqual(result.json['vehicles'][0]['id'], 'old')
        finally:
            server.snapshot = previous


if __name__ == '__main__':
    unittest.main()
