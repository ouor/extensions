package recloudstream

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.DataStore.getKey

class ChzzkProvider : MainAPI() {
    override var mainUrl = "https://chzzk.naver.com"
    override var name = "Chzzk"
    override val supportedTypes = setOf(TvType.Live, TvType.TvSeries)
    override var lang = "ko"
    override val hasMainPage = true
    override val hasQuickSearch = true

    // API Endpoints
    private val API_URL = "https://api.chzzk.naver.com"

    // --- Data Classes ---

    data class ChzzkResponse<T>(
        @JsonProperty("code") val code: Int,
        @JsonProperty("message") val message: String?,
        @JsonProperty("content") val content: T?
    )

    // Common
    data class ChannelInfo(
        @JsonProperty("channelId") val channelId: String,
        @JsonProperty("channelName") val channelName: String,
        @JsonProperty("channelImageUrl") val channelImageUrl: String?,
        @JsonProperty("verifiedMark") val verifiedMark: Boolean?
    )

    // Live Details
    data class LiveDetailContent(
        @JsonProperty("liveId") val liveId: Any?,
        @JsonProperty("liveTitle") val liveTitle: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("liveImageUrl") val liveImageUrl: String?,
        @JsonProperty("defaultThumbnailImageUrl") val defaultThumbnailImageUrl: String?,
        @JsonProperty("concurrentUserCount") val concurrentUserCount: Int?,
        @JsonProperty("openDate") val openDate: String?,
        @JsonProperty("adult") val adult: Boolean?,
        @JsonProperty("categoryType") val categoryType: String?,
        @JsonProperty("liveCategory") val liveCategory: String?,
        @JsonProperty("liveCategoryValue") val liveCategoryValue: String?,
        @JsonProperty("channel") val channel: ChannelInfo?,
        @JsonProperty("livePlaybackJson") val livePlaybackJson: String?
    )

    data class LivePlaybackJson(
        @JsonProperty("media") val media: List<MediaItem>?,
        @JsonProperty("hls") val hls: List<MediaItem>?
    )

    data class MediaItem(
        @JsonProperty("mediaId") val mediaId: String?,
        @JsonProperty("protocol") val protocol: String?,
        @JsonProperty("path") val path: String?,
        @JsonProperty("encodingTrack") val encodingTrack: List<EncodingTrack>?
    )

    data class EncodingTrack(
        @JsonProperty("encodingTrackId") val encodingTrackId: String,
        @JsonProperty("videoProfile") val videoProfile: String,
        @JsonProperty("audioProfile") val audioProfile: String,
        @JsonProperty("videoCodec") val videoCodec: String,
        @JsonProperty("path") val path: String
    )

    // Search
    data class SearchResult(
        @JsonProperty("size") val size: Int,
        @JsonProperty("data") val data: List<SearchData>?
    )

    data class SearchData(
        @JsonProperty("live") val live: LiveDetailContent?,
        @JsonProperty("video") val video: VideoDetailContent?,
        @JsonProperty("channel") val channel: ChannelInfo?
    )

    // VOD
    data class VideoDetailContent(
        @JsonProperty("videoNo") val videoNo: Int?,
        @JsonProperty("videoId") val videoId: String?,
        @JsonProperty("videoTitle") val videoTitle: String?,
        @JsonProperty("videoType") val videoType: String?,
        @JsonProperty("publishDate") val publishDate: String?,
        @JsonProperty("thumbnailImageUrl") val thumbnailImageUrl: String?,
        @JsonProperty("duration") val duration: Int?,
        @JsonProperty("readCount") val readCount: Int?,
        @JsonProperty("adult") val adult: Boolean?,
        @JsonProperty("channel") val channel: ChannelInfo?,
        @JsonProperty("videoCategoryValue") val videoCategoryValue: String?,
        @JsonProperty("inKey") val inKey: String?,
        @JsonProperty("liveRewindPlaybackJson") val liveRewindPlaybackJson: String?
    )

    // Following Response
    data class FollowingResponse(
        @JsonProperty("totalCount") val totalCount: Int,
        @JsonProperty("followingList") val followingList: List<FollowingItem>?
    )

    data class FollowingItem(
        @JsonProperty("channelId") val channelId: String,
        @JsonProperty("channel") val channel: ChannelInfo,
        @JsonProperty("streamer") val StreamerInfo?,
        @JsonProperty("liveInfo") val liveInfo: LiveDetailContent?
    )

    data class StreamerInfo(
        @JsonProperty("openLive") val openLive: Boolean
    )
    
    // Naver VOD API Response (Simplified)
    data class NaverVodResponse(
        @JsonProperty("streams") val streams: List<NaverVodStream>?
    )
    
    data class NaverVodStream(
        @JsonProperty("type") val type: String?,
        @JsonProperty("keys") val keys: List<NaverVodKey>?
    )

    data class NaverVodKey(
         @JsonProperty("type") val type: String?,
         @JsonProperty("value") val value: String?
    )

    // Internal ID to serialize in URL
    data class ChzzkData(
        val type: String, // "live" or "video"
        val id: String
    )

    private fun getCookies(): Map<String, String> {
        val context = app.baseContext
        val nidAut = context.getKey<String>("CHZZK_NID_AUT")
        val nidSes = context.getKey<String>("CHZZK_NID_SES")
        
        return if (!nidAut.isNullOrBlank() && !nidSes.isNullOrBlank()) {
            mapOf("Cookie" to "NID_AUT=$nidAut; NID_SES=$nidSes")
        } else {
            emptyMap()
        }
    }

    private fun getHeaders(): Map<String, String> {
         return mapOf(
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36",
            "Referer" to "https://chzzk.naver.com/"
        ) + getCookies()
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = mutableListOf<HomePageList>()
        
        // 1. Following (LoggedIn)
        val cookies = getCookies()
        if (cookies.isNotEmpty()) {
            try {
                val followingUrl = "$API_URL/service/v1/channels/followings/live"
                val response = app.get(followingUrl, headers = getHeaders()).text
                val followingData = tryParseJson<ChzzkResponse<FollowingResponse>>(response)?.content?.followingList
                
                val followingMapped = followingData?.mapNotNull { item ->
                    val live = item.liveInfo ?: return@mapNotNull null
                    newLiveStreamSearchResponse(
                        live.liveTitle ?: "Unknown",
                        ChzzkData("live", item.channelId).toJson(),
                        TvType.Live
                    ) {
                        this.posterUrl = live.liveImageUrl?.replace("{type}", "480") ?: live.defaultThumbnailImageUrl
                        this.posterHeaders = getHeaders()
                    }
                }
                
                if (!followingMapped.isNullOrEmpty()) {
                    items.add(HomePageList("Following", followingMapped))
                }
            } catch (e: Exception) {
                // Ignore errors
            }
        }

        // 2. Fallback / Main Content
        // Since we lack a generic "Home" API, we try to fetch some popular lives via search with empty keyword or a trick.
        // Actually, just returning an empty list if not logged in is acceptable for a beta.
        // Or we can try searching for "League of Legends" or common categories if we wanted.
        // But let's leave it as Following-only or Empty for now to be safe with APIs.
        
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList? {
        val searchResults = mutableListOf<SearchResponse>()
        val offset = (page - 1) * 12

        // Live Search
        try {
            val liveUrl = "$API_URL/service/v1/search/lives?keyword=$query&offset=$offset&size=12"
            val response = app.get(liveUrl, headers = getHeaders()).text
            val data = tryParseJson<ChzzkResponse<SearchResult>>(response)?.content?.data
            
            data?.forEach { item ->
                val live = item.live ?: return@forEach
                val channel = item.channel ?: item.live.channel ?: return@forEach
                
                searchResults.add(newLiveStreamSearchResponse(
                    live.liveTitle ?: "Unknown",
                    ChzzkData("live", channel.channelId).toJson(),
                    TvType.Live
                ) {
                    this.posterUrl = live.liveImageUrl?.replace("{type}", "480") ?: live.defaultThumbnailImageUrl
                    this.posterHeaders = getHeaders()
                })
            }
        } catch (e: Exception) {}

        // Video Search (VOD)
        try {
            val videoUrl = "$API_URL/service/v1/search/videos?keyword=$query&offset=$offset&size=12"
            val response = app.get(videoUrl, headers = getHeaders()).text
            val data = tryParseJson<ChzzkResponse<SearchResult>>(response)?.content?.data
            
            data?.forEach { item ->
                val video = item.video ?: return@forEach
                
                searchResults.add(newMovieSearchResponse(
                    video.videoTitle ?: "Unknown",
                    ChzzkData("video", video.videoNo.toString()).toJson(),
                    TvType.Movie
                ) {
                    this.posterUrl = video.thumbnailImageUrl
                    this.posterHeaders = getHeaders()
                    this.year = video.publishDate?.take(4)?.toIntOrNull()
                })
            }
        } catch (e: Exception) {}

        return searchResults
    }

    override suspend fun load(url: String): LoadResponse? {
        val data = tryParseJson<ChzzkData>(url) ?: return null
        
        if (data.type == "live") {
            val detailUrl = "$API_URL/service/v3.3/channels/${data.id}/live-detail"
            val response = app.get(detailUrl, headers = getHeaders()).text
            val content = tryParseJson<ChzzkResponse<LiveDetailContent>>(response)?.content ?: return null
            
            return newLiveStreamLoadResponse(
                content.liveTitle ?: "Unknown",
                url, // Keep ID for loadLinks
                TvType.Live
            ) {
                this.posterUrl = content.liveImageUrl?.replace("{type}", "1080") ?: content.defaultThumbnailImageUrl
                this.plot = content.channel?.channelName
                this.tags = listOfNotNull(content.liveCategoryValue, if(content.adult == true) "19+" else null)
            }
        } else {
            // Video
            val detailUrl = "$API_URL/service/v3/videos/${data.id}"
            val response = app.get(detailUrl, headers = getHeaders()).text
            val content = tryParseJson<ChzzkResponse<VideoDetailContent>>(response)?.content ?: return null
            
            return newMovieLoadResponse(
                content.videoTitle ?: "Unknown",
                url,
                TvType.Movie,
                url
            ) {
                this.posterUrl = content.thumbnailImageUrl
                this.plot = content.videoCategoryValue
                this.tags = listOfNotNull(content.videoCategoryValue)
                this.duration = content.duration?.div(60)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val chzzkData = tryParseJson<ChzzkData>(data) ?: return false
        
        if (chzzkData.type == "live") {
            val detailUrl = "$API_URL/service/v3.3/channels/${chzzkData.id}/live-detail"
            val response = app.get(detailUrl, headers = getHeaders()).text
            val content = tryParseJson<ChzzkResponse<LiveDetailContent>>(response)?.content ?: return false
            
            val playbackJson = content.livePlaybackJson ?: return false
            val playbackData = tryParseJson<LivePlaybackJson>(playbackJson) ?: return false
            
            val mediaList = playbackData.hls ?: playbackData.media
            
            mediaList?.filter { it.protocol == "HLS" || it.path?.endsWith(".m3u8") == true }?.forEach { media ->
                media.path?.let { hlsUrl ->
                    callback(ExtractorLink(
                        name, name, hlsUrl, "", Qualities.Unknown.value, ExtractorLinkType.M3U8, headers = getHeaders()
                    ))
                }
            }
        } else {
            // VOD
            val detailUrl = "$API_URL/service/v3/videos/${chzzkData.id}"
            val response = app.get(detailUrl, headers = getHeaders()).text
            val content = tryParseJson<ChzzkResponse<VideoDetailContent>>(response)?.content ?: return false
            
            // 1. Check for liveRewindPlaybackJson (Replay)
            if (!content.liveRewindPlaybackJson.isNullOrBlank()) {
                 val playbackData = tryParseJson<LivePlaybackJson>(content.liveRewindPlaybackJson)
                 playbackData?.media?.forEach { media ->
                      if (media.path != null) { // Usually standard HLS
                          callback(ExtractorLink(name, "Replay", media.path, "", Qualities.Unknown.value, ExtractorLinkType.M3U8, headers = getHeaders()))
                      }
                 }
                 return true
            }

            // 2. Check for VOD (videoId + inKey)
            if (content.videoId != null && content.inKey != null) {
                val vodUrl = "https://apis.naver.com/rmcnmv/rmcnmv/vod/play/v2.0/${content.videoId}?key=${content.inKey}"
                val vodRes = app.get(vodUrl, headers = getHeaders()).text
                
                // Naver VOD response parsing (Manual extraction or JSON)
                // "streams":[{"type":"HLS","keys":[{"type":"hls","value":"...m3u8"}]}]
                // Simplified manual parsing for robustness
                val parsedVod = tryParseJson<NaverVodResponse>(vodRes)
                parsedVod?.streams?.forEach { stream ->
                    stream.keys?.forEach { key ->
                        if (key.type == "hls" && key.value != null) {
                            callback(ExtractorLink(name, "VOD", key.value, "", Qualities.Unknown.value, ExtractorLinkType.M3U8, headers = getHeaders()))
                        }
                    }
                }
            }
        }
        return true
    }
}
