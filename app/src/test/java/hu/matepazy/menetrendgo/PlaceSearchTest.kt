package hu.matepazy.menetrendgo

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class PlaceSearchTest {
    private fun stop(name: String) = Station("id:$name", name, 47.5, 19.0)

    @Test fun goodStopMatchKeepsPlacesHidden() {
        assertFalse(stopMatchIsWeak("győr", listOf(stop("Győr*"), stop("Győr, autóbusz-állomás"))))
        assertFalse(stopMatchIsWeak("Deli pu", listOf(stop("Budapest-Déli pu."))))
        assertFalse(stopMatchIsWeak("Szeged", listOf(stop("Szeged"))))
    }

    @Test fun weakOrMissingStopMatchShowsPlaces() {
        assertTrue(stopMatchIsWeak("Westend", emptyList()))
        assertTrue(stopMatchIsWeak("Westend", listOf(stop("Budapest, Nyugati pályaudvar"))))
        assertTrue(stopMatchIsWeak("Kossuth Lajos utca 12 Győr", listOf(stop("Győr, Kossuth Lajos utca"))))
        // MÁV also lists foreign stations; a prefix match abroad is not a good match.
        assertTrue(stopMatchIsWeak("Westend", listOf(Station("a", "Westendorf", 47.43, 12.21))))
        assertFalse(stopMatchIsWeak("  ", emptyList()))
    }

    private val photon = JSONObject("""{"features":[
        {"properties":{"osm_type":"W","osm_id":24000485,"osm_key":"shop","osm_value":"mall","type":"house","housenumber":"1-3","name":"Westend","street":"Váci út","city":"Budapest","countrycode":"HU"},"geometry":{"coordinates":[19.0589765,47.5132499]}},
        {"properties":{"osm_type":"R","osm_id":1,"osm_key":"landuse","osm_value":"commercial","type":"locality","name":"Westend","countrycode":"SK"},"geometry":{"coordinates":[17.07,48.17]}},
        {"properties":{"osm_type":"N","osm_id":2,"osm_key":"highway","osm_value":"bus_stop","type":"house","name":"Westend","city":"Budapest","countrycode":"HU"},"geometry":{"coordinates":[19.05,47.51]}},
        {"properties":{"osm_type":"W","osm_id":3,"osm_key":"building","osm_value":"yes","type":"house","housenumber":"12","street":"Kossuth Lajos utca","city":"Győr","countrycode":"HU"},"geometry":{"coordinates":[17.62,47.68]}},
        {"properties":{"osm_type":"W","osm_id":4,"osm_key":"highway","osm_value":"residential","type":"street","name":"Kossuth Lajos utca","city":"Győr","countrycode":"HU"},"geometry":{"coordinates":[17.63,47.69]}}
    ]}""")

    @Test fun parsesHungarianPlacesAndSkipsStopsAndNeighbours() {
        val places = parsePlaces(photon)
        assertEquals(listOf("osm:W24000485", "osm:W3", "osm:W4"), places.map { it.id })
        val mall = places[0]
        assertEquals("Westend", mall.name)
        assertEquals("Bevásárlóközpont · Budapest, Váci út 1-3", mall.detail)
        assertEquals(47.5132499, mall.lat, 1e-9)
        assertEquals(PlaceKind.Named, mall.placeKind())
        assertEquals("Kossuth Lajos utca 12", places[1].name)
        assertEquals("Cím · Győr", places[1].detail)
        assertEquals(PlaceKind.Address, places[1].placeKind())
        assertEquals(PlaceKind.Street, places[2].placeKind())
        assertTrue(places.all { it.isPlace })
    }

    @Test fun streetsRankBelowPlaces() {
        val streetFirst = JSONObject("""{"features":[
            {"properties":{"osm_type":"W","osm_id":10,"osm_key":"highway","osm_value":"primary","type":"street","name":"Petőfi utca","city":"Szeged","countrycode":"HU"},"geometry":{"coordinates":[20.14,46.25]}},
            {"properties":{"osm_type":"N","osm_id":11,"osm_key":"tourism","osm_value":"museum","type":"house","name":"Petőfi Irodalmi Múzeum","city":"Budapest","countrycode":"HU"},"geometry":{"coordinates":[19.06,47.49]}},
            {"properties":{"osm_type":"N","osm_id":12,"osm_key":"amenity","osm_value":"school","type":"house","name":"Petőfi Sándor Általános Iskola","city":"Kecskemét","countrycode":"HU"},"geometry":{"coordinates":[19.69,46.9]}}
        ]}""")
        assertEquals(listOf("osm:N11", "osm:N12", "osm:W10"), parsePlaces(streetFirst).map { it.id })
        // Streets also give way when the result limit cuts the list.
        assertEquals(listOf("osm:N11"), parsePlaces(streetFirst, limit = 1).map { it.id })
    }

    @Test fun placesSurviveStationPersistence() {
        val place = parsePlaces(photon).first()
        assertEquals(place, Station.parse(place.json()))
        assertFalse(stop("Győr").json().has("detail"))
    }

    @Test fun placesRouteByCoordinates() {
        val place = parsePlaces(photon).first()
        val query = MavQueries.plan(place, stop("Győr"), LocalDateTime.of(2026, 11, 19, 8, 0), false)
        assertTrue(query.contains("from:{lat:47.5132499,lon:19.0589765}"))
        assertFalse(query.contains("osm:"))
        assertTrue(query.contains("toPlace:\"Győr::id:Győr\""))
    }
}
