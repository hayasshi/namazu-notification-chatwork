package com.github.hayasshi.n2.chatwork

case class YahooMyThings(
    user_id: String,
    service_id: String,
    mythings_id: String,
    values: Seq[YahooEarthQuake]
)

case class YahooEarthQuake(
    place_name: String,
    intensity: String,
    max_intensity: String,
    occurrence_date: String,
    occurrence_time: String,
    url: String
)
