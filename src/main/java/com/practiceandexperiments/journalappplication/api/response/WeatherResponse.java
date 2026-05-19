package com.practiceandexperiments.journalappplication.api.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class WeatherResponse{
    private Current current;
//    private Request request;
//    private Location location;

    @Getter
    @Setter
    public class Current{
        private int temperature;

        @JsonProperty("weather_descriptions")
        private List<String> weatherDescriptions;

        private int feelslike;

        // we can use all the below-given properties if we want to, but we don't have their requirement right now

//        @JsonProperty("weather_code")
//        private int weatherCode;
//        @JsonProperty("observation_time")
//        private String observationTime;
//        @JsonProperty("weather_icons")
//        private List<String> weatherIcons;
//        private Astro astro;
//        @JsonProperty("air_quality")
//        private AirQuality airQuality;
//        @JsonProperty("wind_speed")
//        private int windSpeed;
//        @JsonProperty("wind_degree")
//        private int windDegree;
//        @JsonProperty("wind_dir")
//        private String windDir;
//        private int pressure;
//        private int precip;
//        private int humidity;
//        private int cloudcover;
//        @JsonProperty("uv_index")
//        private int uvIndex;
//        private int visibility;
//        @JsonProperty("is_day")
//        private String isDay;
    }

//    @Getter
//    @Setter
//    public class AirQuality{
//        private String co;
//        private String no2;
//        private String o3;
//        private String so2;
//        private String pm2_5;
//        private String pm10;
//        @JsonProperty("us-epa-index")
//        private String usEpaIndex;
//        @JsonProperty("gb-defra-index")
//        private String gbDefraIndex;
//    }
//
//    @Getter
//    @Setter
//    public class Astro{
//        private String sunrise;
//        private String sunset;
//        private String moonrise;
//        private String moonset;
//        @JsonProperty("moon_phase")
//        private String moonPhase;
//        @JsonProperty("moon_illumination")
//        private int moonIllumination;
//    }
//
//    @Getter
//    @Setter
//    public class Location{
//        private String name;
//        private String country;
//        private String region;
//        private String lat;
//        private String lon;
//        @JsonProperty("timezone_id")
//        private String timezoneId;
//        private String localtime;
//        @JsonProperty("localtime_epoch")
//        private int localtimeEpoch;
//        @JsonProperty("utc_offset")
//        private String utcOffset;
//    }
//
//    @Getter
//    @Setter
//    public class Request{
//        private String type;
//        private String query;
//        private String language;
//        private String unit;
//    }
}


