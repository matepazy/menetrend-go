"""MÁV proxy following hovamegy.hu: Flask + OTP GraphQL, 75-second shared cache.

No accounts or passenger data. A single process owns the polling thread; serve
with Waitress, not a multi-process worker that would duplicate upstream polling.
"""
import json
import logging
import math
import os
import threading
import time
from datetime import datetime
from zoneinfo import ZoneInfo

import requests
from flask import Flask, jsonify, request

app = Flask(__name__)
app.config['MAX_CONTENT_LENGTH'] = 16 * 1024
ROOT = 'https://mavplusz.hu/otp2-backend/otp/routers/default'
INTERVAL = 75
HEADERS = {'Content-Type': 'application/json', 'Origin': 'https://mavplusz.hu',
           'Referer': 'https://mavplusz.hu/', 'User-Agent': 'MenetrendGO/0.1'}
lock = threading.Lock()
snapshot = {'lastUpdated': 0, 'vehicles': []}
trip_cache = {}


def graphql(query):
    response = requests.post(ROOT + '/index/graphql', headers=HEADERS,
                             json={'query': query}, timeout=(5, 30))
    response.raise_for_status()
    body = response.json()
    if body.get('errors') or body.get('data') is None:
        raise ValueError('MÁV returned an incomplete response')
    return body['data']


@app.errorhandler(requests.RequestException)
@app.errorhandler(ValueError)
def upstream_error(error):
    app.logger.warning('Upstream request failed: %s', type(error).__name__)
    return jsonify(error='A MÁV adatai most nem érhetők el. Próbáld újra.'), 502


def refresh_vehicles():
    data = graphql('''{vehiclePositions(swLat:45.5,swLon:16.1,neLat:48.7,neLon:22.8,
      modes:[RAIL,RAIL_REPLACEMENT_BUS,COACH,SUBURBAN_RAILWAY,TRAMTRAIN]) {
      vehicleId lat lon speed heading label
      trip {gtfsId tripShortName tripHeadsign route {mode}}}}''')
    vehicles = []
    for v in data.get('vehiclePositions') or []:
        trip = v.get('trip') or {}
        if not trip.get('gtfsId') or v.get('lat') is None or v.get('lon') is None:
            continue
        vehicles.append(dict(id=trip['gtfsId'], vehicleId=v.get('vehicleId'),
            name=trip.get('tripShortName') or v.get('label') or 'Járat',
            headsign=trip.get('tripHeadsign') or '', lat=v['lat'], lon=v['lon'],
            mode=(trip.get('route') or {}).get('mode'), speed=v.get('speed')))
    global snapshot
    with lock:
        snapshot = {'lastUpdated': int(time.time()), 'vehicles': vehicles}


def poll_forever():
    while True:
        start = time.monotonic()
        try:
            refresh_vehicles()
        except (requests.RequestException, ValueError, KeyError):
            app.logger.exception('Vehicle refresh failed; retaining last good snapshot')
        time.sleep(max(1, INTERVAL - (time.monotonic() - start)))


@app.get('/health')
def health():
    with lock:
        updated = snapshot['lastUpdated']
    return jsonify(status='ok', refreshSeconds=INTERVAL, lastUpdated=updated)


@app.get('/live_data.json')
def live():
    with lock:
        data = dict(snapshot)
    data['stale'] = time.time() - data['lastUpdated'] > 180
    if not data['lastUpdated']:
        return jsonify(error='A járműadatok még betöltés alatt vannak.', **data), 503
    response = jsonify(data)
    response.headers['Cache-Control'] = 'no-store'
    return response


@app.get('/api/stations')
def stations():
    q = request.args.get('q', '').strip()
    if not 2 <= len(q) <= 100:
        return jsonify(error='Adj meg legalább két karaktert.'), 400
    response = requests.get(ROOT + '/geocode/stations', params={'q': q, 'limit': 10},
                            headers=HEADERS, timeout=(5, 15))
    response.raise_for_status()
    items = []
    for f in response.json().get('features', []):
        p = f.get('properties') or {}
        coords = (f.get('geometry') or {}).get('coordinates') or []
        if len(coords) >= 2:
            items.append({'id': str(f.get('id', '')), 'name': p.get('name', q),
                          'lat': coords[1], 'lon': coords[0]})
    return jsonify(stations=items)


def coordinates(value):
    if not isinstance(value, dict):
        raise TypeError('Missing coordinates')
    lat, lon = value.get('lat'), value.get('lon')
    if any(isinstance(v, bool) or not isinstance(v, (int, float)) or not math.isfinite(v)
           for v in (lat, lon)) or not -90 <= lat <= 90 or not -180 <= lon <= 180:
        raise TypeError('Invalid coordinates')
    return f'{{lat:{lat},lon:{lon}}}'


@app.post('/api/plan')
def plan():
    body = request.get_json(silent=True)
    try:
        if not isinstance(body, dict):
            raise TypeError('Missing body')
        origin, destination = coordinates(body.get('from')), coordinates(body.get('to'))
        dt = datetime.fromisoformat(body['dateTime'])
        if dt.tzinfo:
            dt = dt.astimezone(ZoneInfo('Europe/Budapest'))
        arrive = body.get('arriveBy', False)
        if not isinstance(arrive, bool):
            raise TypeError('arriveBy must be boolean')
    except (TypeError, KeyError, ValueError):
        return jsonify(error='Hiányzó vagy hibás megálló / időpont.'), 400
    query = '''{plan(from:%s,to:%s,date:%s,time:%s,arriveBy:%s,numItineraries:12,
      transportModes:[{mode:RAIL},{mode:COACH},{mode:BUS},{mode:SUBURBAN_RAILWAY},{mode:TRAMTRAIN},{mode:WALK}],
      minTransferTime:120,maxWalkDistance:2000) {itineraries {duration walkTime waitingTime
      legs {mode startTime endTime duration distance realTime departureDelay arrivalDelay
        from {name lat lon} to {name lat lon}
        intermediateStops {name lat lon} legGeometry {points}
        route {shortName longName mode} trip {gtfsId tripShortName tripHeadsign}}}}}''' % (
        origin, destination, json.dumps(dt.strftime('%Y-%m-%d')),
        json.dumps(dt.strftime('%H:%M')), str(arrive).lower())
    result = graphql(query).get('plan') or {}
    itineraries = result.get('itineraries') or []
    for itinerary in itineraries:
        for leg in itinerary.get('legs') or []:
            route = leg.get('route') or {}
            if '<' in (route.get('shortName') or ''):
                route['shortName'] = (leg.get('trip') or {}).get('tripShortName') or 'Vonat'
            for key, placeholder, fallback in (('from', 'Origin', 'Indulási pont'), ('to', 'Destination', 'Érkezési pont')):
                place = leg.get(key) or {}
                if place.get('name') == placeholder:
                    name = (body.get(key) or {}).get('name')
                    place['name'] = name if isinstance(name, str) and len(name) <= 200 else fallback
    itineraries.sort(key=lambda i: (i.get('legs') or [{}])[0].get('startTime', 0))
    return jsonify(plan={'itineraries': itineraries}, fetchedAt=int(time.time()))


@app.get('/api/trip')
def trip():
    trip_id = request.args.get('id', '')
    day = request.args.get('date', datetime.now(ZoneInfo('Europe/Budapest')).date().isoformat())
    try:
        datetime.strptime(day, '%Y-%m-%d')
        if not 1 <= len(trip_id) <= 250:
            raise ValueError()
    except ValueError:
        return jsonify(error='Hibás járatazonosító vagy dátum.'), 400
    key = (trip_id, day)
    with lock:
        cached = trip_cache.get(key)
    if cached and time.time() - cached[0] < INTERVAL:
        return jsonify(cached[1])
    query = '''{trip(id:%s,serviceDay:%s) {gtfsId tripHeadsign trainCategoryName trainName
      tripShortName route {shortName longName(language:"hu")}
      stoptimes {serviceDay realtime stop {name lat lon platformCode} realtimeArrival realtimeDeparture
        arrivalDelay departureDelay scheduledArrival scheduledDeparture}}}''' % (
        json.dumps(trip_id), json.dumps(day))
    result = {'trip': graphql(query).get('trip'), 'fetchedAt': int(time.time())}
    if not result['trip']:
        return jsonify(error='Ehhez a járathoz nincs részletes menetrend.'), 404
    route = result['trip'].get('route') or {}
    # MÁV sometimes sends HTML icon markup rather than a route number.
    if '<' in (route.get('shortName') or ''):
        route['shortName'] = result['trip'].get('tripShortName') or result['trip'].get('trainCategoryName') or 'Vonat'
    with lock:
        if len(trip_cache) > 512:
            trip_cache.clear()
        trip_cache[key] = (time.time(), result)
    return jsonify(result)


if __name__ == '__main__':
    from waitress import serve
    logging.basicConfig(level=logging.INFO)
    threading.Thread(target=poll_forever, daemon=True).start()
    serve(app, host='0.0.0.0', port=int(os.environ.get('PORT', 8000)), threads=8)
