package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class DeviceConfigTest {
    @Test public void cleanDefaultHasNoPersonalLocationOrVendorOverride() throws Exception { DeviceConfig config=DeviceConfig.parse("{}"); assertFalse(config.hasWeather()); assertEquals("",config.liveTv); assertTrue(config.dockApps.isEmpty()); }
    @Test public void explicitDeviceConfigIsValidated() throws Exception {
        DeviceConfig config=DeviceConfig.parse("{\"liveTvPackage\":\"com.example.tv\",\"weather\":{\"latitude\":1.5,\"longitude\":-2.5},\"dockApps\":[\"com.example.app\"]}");
        assertTrue(config.hasWeather()); assertEquals("com.example.tv",config.liveTv);
        for(String invalid:new String[]{"{\"weather\":{\"latitude\":91,\"longitude\":0}}","{\"liveTvPackage\":\"bad;command\"}","{\"unknown\":true}","{\"dockApps\":[\"bad package\"]}"}) assertThrows(Exception.class,() -> DeviceConfig.parse(invalid));
    }
    @Test public void gridFitsNarrowTvWidths() { assertEquals(3,DeviceConfig.columns(1280,2,5)); assertEquals(5,DeviceConfig.columns(1920,2,5)); assertEquals(5,DeviceConfig.columns(3840,3,5)); }
}
