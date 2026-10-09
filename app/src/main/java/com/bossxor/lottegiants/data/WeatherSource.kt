package com.bossxor.lottegiants.data

import com.bossxor.lottegiants.domain.*
import java.util.concurrent.ConcurrentHashMap

internal class WeatherSource(private val weatherApi: WeatherApi) {
    private val flight = SingleFlight<String, StadiumWeather>()
    private val cache = ConcurrentHashMap<String, Pair<Long, StadiumWeather>>()
    suspend fun get(
        stadium: String,
        fallbackTeamCode: String = LOTTE_TEAM_CODE,
    ): StadiumWeather {
        val coord = resolveStadiumCoord(stadium, teamHomeStadiumName(fallbackTeamCode))
        val cached = cache[coord.name]
        if (cached != null && System.nanoTime() - cached.first in 0 until 900_000_000_000L) return cached.second
        return flight.run(coord.name) {
        val res = weatherApi.current(coord.lat, coord.lon)
        val cur = res.current
        val code = cur?.weather_code ?: 0
        StadiumWeather(
            stadium = coord.name,
            temperatureC = cur?.temperature_2m ?: 0.0,
            weatherCode = code,
            precipProbability = cur?.precipitation_probability,
            summary = weatherSummaryKo(code),
            updatedAt = cur?.time.orEmpty(),
        ).also { cache[coord.name] = System.nanoTime() to it }
        }
    }

}
