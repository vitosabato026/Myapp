package it.promozionepuglia.gironeb.data.remote

import it.promozionepuglia.gironeb.data.NewsItem
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

/** Legge l'elenco articoli dall'API WordPress pubblica di salentosport.net. */
object SalentoSportParser {

    private val json = Json { ignoreUnknownKeys = true }

    fun parsePosts(body: String): List<NewsItem> =
        (json.parseToJsonElement(body) as? JsonArray).orEmpty().mapNotNull { el ->
            val post = el as? JsonObject ?: return@mapNotNull null
            val id = post["id"]?.jsonPrimitive?.longOrNull ?: return@mapNotNull null
            NewsItem(
                id = id,
                title = htmlToText(post.rendered("title")),
                excerpt = htmlToText(post.rendered("excerpt")).removeSuffix("[…]").trim(),
                url = post["link"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                imageUrl = (post["yoast_head_json"] as? JsonObject)
                    ?.get("og_image")?.jsonArray?.firstOrNull()
                    ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                date = post["date"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                source = "SalentoSport",
            )
        }

    private fun JsonObject.rendered(field: String): String =
        (this[field] as? JsonObject)?.get("rendered")?.jsonPrimitive?.contentOrNull.orEmpty()

    private fun htmlToText(html: String): String = Jsoup.parse(html).text().trim()
}
