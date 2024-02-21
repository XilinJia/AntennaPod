package ac.mdiq.podcini.net.discovery

import ac.mdiq.podcini.net.sync.gpoddernet.model.GpodnetPodcast
import de.mfietz.fyydlin.SearchHit
import org.json.JSONException
import org.json.JSONObject

class PodcastSearchResult private constructor(
        /**
         * The name of the podcast
         */
        val title: String,
        /**
         * URL of the podcast image
         */
        val imageUrl: String?,
        /**
         * URL of the podcast feed
         */
        val feedUrl: String?,
        /**
         * artistName of the podcast feed
         */
        val author: String?
) {
    companion object {
        fun dummy(): PodcastSearchResult {
            return PodcastSearchResult("", "", "", "")
        }

        /**
         * Constructs a Podcast instance from a iTunes search result
         *
         * @param json object holding the podcast information
         * @throws JSONException
         */
        fun fromItunes(json: JSONObject): PodcastSearchResult {
            val title = json.optString("collectionName", "")
            val imageUrl: String? = json.optString("artworkUrl100").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("feedUrl").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("artistName").takeIf { it.isNotEmpty() }
            return PodcastSearchResult(title, imageUrl, feedUrl, author)
        }

        /**
         * Constructs a Podcast instance from iTunes toplist entry
         *
         * @param json object holding the podcast information
         * @throws JSONException
         */
        @Throws(JSONException::class)
        fun fromItunesToplist(json: JSONObject): PodcastSearchResult {
            val title = json.getJSONObject("title").getString("label")
            var imageUrl: String? = null
            val images = json.getJSONArray("im:image")
            var i = 0
            while (imageUrl == null && i < images.length()) {
                val image = images.getJSONObject(i)
                val height = image.getJSONObject("attributes").getString("height")
                if (height.toInt() >= 100) {
                    imageUrl = image.getString("label")
                }
                i++
            }
            val feedUrl = "https://itunes.apple.com/lookup?id=" +
                    json.getJSONObject("id").getJSONObject("attributes").getString("im:id")

            var author: String? = null
            try {
                author = json.getJSONObject("im:artist").getString("label")
            } catch (e: Exception) {
                // Some feeds have empty artist
            }
            return PodcastSearchResult(title, imageUrl, feedUrl, author)
        }

        fun fromFyyd(searchHit: SearchHit): PodcastSearchResult {
            return PodcastSearchResult(searchHit.title,
                searchHit.thumbImageURL,
                searchHit.xmlUrl,
                searchHit.author)
        }

        fun fromGpodder(searchHit: GpodnetPodcast): PodcastSearchResult {
            return PodcastSearchResult(searchHit.title,
                searchHit.logoUrl,
                searchHit.url,
                searchHit.author)
        }

        fun fromPodcastIndex(json: JSONObject): PodcastSearchResult {
            val title = json.optString("title", "")
            val imageUrl: String? = json.optString("image").takeIf { it.isNotEmpty() }
            val feedUrl: String? = json.optString("url").takeIf { it.isNotEmpty() }
            val author: String? = json.optString("author").takeIf { it.isNotEmpty() }
            return PodcastSearchResult(title, imageUrl, feedUrl, author)
        }
    }
}