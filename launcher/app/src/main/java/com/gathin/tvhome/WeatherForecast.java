package com.gathin.tvhome;

import org.json.JSONArray;
import org.json.JSONObject;

final class WeatherForecast {
    private static int probability(JSONArray values,int hour) {
        if (values==null||hour<0||hour>=values.length()||values.isNull(hour)) return -1;
        int p=values.optInt(hour,-1); return p>=0&&p<=100?p:-1;
    }
    static String summary(JSONObject current,JSONObject hourly) throws Exception {
        String suffix=Math.round(current.getDouble("temperature_2m"))+"°C";
        JSONArray probabilities=hourly==null?null:hourly.optJSONArray("precipitation_probability");
        JSONArray times=hourly==null?null:hourly.optJSONArray("time");
        String now=current.optString("time",""); int index=-1;
        if (now.matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}.*")&&times!=null) {
            String hour=now.substring(0,13);
            for (int i=0;i<times.length();i++) if (times.optString(i).equals(hour+":00")) { index=i; break; }
        }
        // The source is a regional model, not a rain sensor at the user's address.
        int p=probability(probabilities,index);
        return suffix+(p>=0?"  •  CHANCE DE CHUVA "+p+"%":"");
    }
}
