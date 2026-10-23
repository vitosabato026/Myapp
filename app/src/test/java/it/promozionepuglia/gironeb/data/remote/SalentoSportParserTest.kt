package it.promozionepuglia.gironeb.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class SalentoSportParserTest {

    @Test
    fun parsesWordPressPosts() {
        val body = """
            [{"id":280678,"date":"2026-10-22T17:45:50",
              "link":"https:\/\/www.salentosport.net\/calcio\/promozione\/coppa\/",
              "title":{"rendered":"PROMOZIONE &#8211; Coppa Italia: risultati"},
              "excerpt":{"rendered":"<p>Ottavi di finale&hellip;<\/p>\n"},
              "yoast_head_json":{"og_image":[{"url":"https:\/\/www.salentosport.net\/img.jpeg"}]}},
             {"id":2,"date":"2026-10-21T16:45:10","link":"https:\/\/x\/","title":{"rendered":"Senza immagine"},"excerpt":{"rendered":""}}]
        """.trimIndent()
        val news = SalentoSportParser.parsePosts(body)
        assertEquals(2, news.size)
        assertEquals("PROMOZIONE – Coppa Italia: risultati", news[0].title)
        assertEquals("Ottavi di finale…", news[0].excerpt)
        assertEquals("https://www.salentosport.net/img.jpeg", news[0].imageUrl)
        assertEquals("https://www.salentosport.net/calcio/promozione/coppa/", news[0].url)
        assertEquals(null, news[1].imageUrl)
    }
}
