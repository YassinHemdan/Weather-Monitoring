package org.example.model;

public class WeatherMessage {
    private long station_id;
    private static long s_no = 0;
    private Status battery_status;
    private long status_timestamp;
    private WeatherInfo weather;

    public WeatherMessage(long station_id, WeatherInfo weather, long status_timestamp, Status battery_status) {
        this.station_id = station_id;
        this.weather = weather;
        this.status_timestamp = status_timestamp;
        this.battery_status = battery_status;
        ++s_no;
    }

    public long getStation_id() {
        return station_id;
    }

    public void setStation_id(long station_id) {
        this.station_id = station_id;
    }

    public WeatherInfo getWeather() {
        return weather;
    }

    public void setWeather(WeatherInfo weather) {
        this.weather = weather;
    }

    public long getStatus_timestamp() {
        return status_timestamp;
    }

    public void setStatus_timestamp(long status_timestamp) {
        this.status_timestamp = status_timestamp;
    }

    public static long getS_no() {
        return s_no;
    }

    public Status getBattery_status() {
        return battery_status;
    }

    public void setBattery_status(Status battery_status) {
        this.battery_status = battery_status;
    }
}
