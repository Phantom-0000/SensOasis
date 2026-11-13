package es.upm.sensoasis

import es.upm.sensoasis.data.opendata.MadridFountainsParser
import es.upm.sensoasis.domain.model.FountainStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MadridFountainsParserTest {
    @Test fun parsesMadridOpenDataRowsAndWgs84Coordinates() {
        val source = """
            [{
              "ID":"10030549",
              "ESTADO":"OPERATIVO",
              "COORD_GIS_X":"447452.205",
              "COORD_GIS_Y":"4469608.527",
              "SISTEMA_COORD":"ETRS89",
              "LATITUD":40.37540321,
              "LONGITUD":-3.61900539,
              "TIPO_VIA":"CALLE",
              "NOM_VIA":"MUELA DE SAN JUAN",
              "NUM_VIA":"5",
              "CODIGO_INTERNO":"FUE_18_0089"
            }]
        """.trimIndent()

        val fountain = MadridFountainsParser.parse(source).single()

        assertEquals("10030549", fountain.id)
        assertEquals("Fuente FUE_18_0089", fountain.name)
        assertEquals(FountainStatus.WORKING, fountain.officialStatus)
        assertEquals(40.37540321, fountain.coordinates.latitude, 0.000001)
        assertEquals(-3.61900539, fountain.coordinates.longitude, 0.000001)
        assertTrue(fountain.address.contains("MUELA DE SAN JUAN"))
    }

    @Test fun convertsUtmCoordinatesWhenLatitudeAndLongitudeAreNull() {
        val source = """
            [{
              "ID":"10040804",
              "ESTADO":"NO OPERATIVO",
              "COORD_GIS_X":"447614.36",
              "COORD_GIS_Y":"4481601.607",
              "SISTEMA_COORD":"ETRS89",
              "LATITUD":null,
              "LONGITUD":null
            }]
        """.trimIndent()

        val fountain = MadridFountainsParser.parse(source).single()

        assertEquals(FountainStatus.NOT_WORKING, fountain.officialStatus)
        assertEquals(40.48, fountain.coordinates.latitude, 0.02)
        assertEquals(-3.62, fountain.coordinates.longitude, 0.02)
    }

    @Test fun continuesToParseBundledSimplifiedArrayFormat() {
        val source = """
            [{"id":"MADRID_FT_042","name":"Fuente Puerta del Sol","latitude":40.4169,
              "longitude":-3.7035,"officialStatus":"WORKING","address":"Madrid"}]
        """.trimIndent()

        val fountain = MadridFountainsParser.parse(source).single()

        assertEquals("MADRID_FT_042", fountain.id)
        assertEquals("Fuente Puerta del Sol", fountain.name)
        assertEquals(FountainStatus.WORKING, fountain.officialStatus)
    }

    @Test fun parsesTheBundledFallbackAsset() {
        val source = File("src/main/assets/samples/fountains_sample.json").readText()

        val fountains = MadridFountainsParser.parse(source)

        assertEquals(5, fountains.size)
        assertTrue(fountains.any { it.id == "MADRID_FT_042" })
    }
}
