package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class WeatherForecastTest {
    private JSONObject current() throws Exception { return new JSONObject().put("temperature_2m",28.4).put("time","2026-10-23T07:45"); }
    private JSONObject hourly(int p) throws Exception {
        JSONArray times=new JSONArray(),probs=new JSONArray();
        for (int i=0;i<24;i++) { times.put(String.format(java.util.Locale.ROOT,"2026-10-23T%02d:00",i)); probs.put(p); }
        return new JSONObject().put("time",times).put("precipitation_probability",probs);
    }
    @Test public void usesSourceLocalTimestampInsteadOfDeviceHour() throws Exception {
        JSONObject h=hourly(80); h.getJSONArray("precipitation_probability").put(7,1);
        assertEquals("28°C  •  CHANCE DE CHUVA 1%",WeatherForecast.summary(current(),h));
    }
    @Test public void futureRainDoesNotReplaceCurrentProbability() throws Exception {
        JSONObject h=hourly(10); h.getJSONArray("precipitation_probability").put(8,90);
        assertEquals("28°C  •  CHANCE DE CHUVA 10%",WeatherForecast.summary(current(),h));
    }
    @Test public void modelRainNeverClaimsObservedRain() throws Exception {
        assertEquals("28°C  •  CHANCE DE CHUVA 90%",WeatherForecast.summary(current().put("weather_code",61).put("precipitation",1),hourly(90)));
    }
    @Test public void missingInvalidAndOtherDatesDoNotInventZero() throws Exception {
        assertEquals("28°C",WeatherForecast.summary(current(),null));
        assertEquals("28°C",WeatherForecast.summary(current(),hourly(150)));
        assertEquals("28°C",WeatherForecast.summary(current().put("time","2026-10-24T07:00"),hourly(50)));
        assertEquals("28°C",WeatherForecast.summary(current().put("time",""),hourly(50)));
        JSONObject h=hourly(0); h.getJSONArray("precipitation_probability").put(7,JSONObject.NULL);
        assertEquals("28°C",WeatherForecast.summary(current(),h));
    }
    @Test public void handlesForecastStartingOnPreviousDay() throws Exception {
        JSONObject h=new JSONObject().put("time",new JSONArray().put("2026-10-22T23:00").put("2026-10-23T00:00"))
            .put("precipitation_probability",new JSONArray().put(80).put(2));
        assertEquals("28°C  •  CHANCE DE CHUVA 2%",WeatherForecast.summary(current().put("time","2026-10-23T00:15"),h));
    }
}
