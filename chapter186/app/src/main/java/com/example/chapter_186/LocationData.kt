package com.example.chapter_186

data class LocationData(
    val latitude: Double,
    val longitude: Double
)

data class GeocodingResult(
    val formatted_address: String
)

data class GeocodingResponse(
    val results: List<GeocodingResult>,
    val status: String
)
