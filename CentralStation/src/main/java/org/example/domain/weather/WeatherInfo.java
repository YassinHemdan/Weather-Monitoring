package org.example.domain.weather;


public class WeatherInfo {
    private int humidity;
    private int temperature;
    private int wind_speed;

    public WeatherInfo(){}

    public WeatherInfo(int wind_speed, int temperature, int humidity) {
        this.wind_speed = wind_speed;
        this.temperature = temperature;
        this.humidity = humidity;
    }

    public int getHumidity() {
        return humidity;
    }

    public void setHumidity(int humidity) {
        this.humidity = humidity;
    }

    public int getWind_speed() {
        return wind_speed;
    }

    public void setWind_speed(int wind_speed) {
        this.wind_speed = wind_speed;
    }

    public int getTemperature() {
        return temperature;
    }

    public void setTemperature(int temperature) {
        this.temperature = temperature;
    }
}
