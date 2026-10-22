package com.entretv.app.data.remote

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

interface XtreamApi {

    @GET
    suspend fun login(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): XtreamLogin

    @GET
    suspend fun liveCategories(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_categories"
    ): List<XtreamCategory>

    @GET
    suspend fun liveStreams(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_live_streams"
    ): List<XtreamStream>

    @GET
    suspend fun vodCategories(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_categories"
    ): List<XtreamCategory>

    @GET
    suspend fun vodStreams(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_vod_streams"
    ): List<XtreamVodStream>

    @GET
    suspend fun seriesCategories(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series_categories"
    ): List<XtreamCategory>

    @GET
    suspend fun seriesList(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String = "get_series"
    ): List<XtreamSeries>

    // Se leen como JSON crudo: muchos paneles mandan "info": [] o tipos mezclados.
    @GET
    suspend fun seriesInfo(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("series_id") seriesId: Int,
        @Query("action") action: String = "get_series_info"
    ): JsonObject

    @GET
    suspend fun vodInfo(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("vod_id") vodId: Int,
        @Query("action") action: String = "get_vod_info"
    ): JsonObject

    @GET
    suspend fun shortEpg(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("stream_id") streamId: Int,
        @Query("action") action: String = "get_short_epg"
    ): XtreamEpgResponse

    @GET
    suspend fun fetchText(@Url url: String): ResponseBody

    /** Inicio de sesión en crudo: los paneles responden objeto, lista vacía o HTML según el error. */
    @GET
    suspend fun loginRaw(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String
    ): ResponseBody

    /** Respuesta cruda de cualquier acción; el repositorio la decodifica y reintenta si llega cortada. */
    @GET
    @Streaming
    suspend fun action(
        @Url baseUrl: String,
        @Query("username") username: String,
        @Query("password") password: String,
        @Query("action") action: String
    ): ResponseBody
}
