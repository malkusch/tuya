package de.malkusch.tuya.openhab;

import java.io.IOException;
import java.time.Duration;

import com.google.gson.Gson;

import de.malkusch.tuya.TuyaApi;
import de.malkusch.tuya.openhab.api.Api;
import de.malkusch.tuya.openhab.api.Api.Power;
import de.malkusch.tuya.openhab.api.Device;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public final class OpenhabTuyaApi implements TuyaApi {

    @RequiredArgsConstructor
    public final static class Factory implements TuyaApi.Factory {

        private final Api.Factory apiFactory;
        private final Device.Factory deviceFactory;

        public Factory(Duration deviceTimout, Duration discoverTyime, Duration expiration) {
            this(new Gson(), discoverTyime, deviceTimout, expiration);
        }

        public Factory(Gson gson, Duration discoveryTimeout, Duration timeout, Duration expiration) {
            apiFactory = new Api.Factory(timeout, expiration);
            deviceFactory = new Device.Factory(gson, discoveryTimeout);
        }

        @Override
        public TuyaApi api(String deviceId, String localKey) throws IOException {
            var device = deviceFactory.device(deviceId, localKey);
            var api = apiFactory.api(device);
            return new OpenhabTuyaApi(api);
        }

        @Override
        public void close() throws Exception {
            deviceFactory.close();
        }
    }

    private final Api openhab;

    @Override
    public void turnOn() throws IOException {
        openhab.send(Power.ON);
    }

    @Override
    public void turnOff() throws IOException {
        openhab.send(Power.OFF);
    }

    @Override
    public boolean isOn() throws IOException {
        return openhab.state().power() == Power.ON;
    }

    @Override
    public boolean isOnline() {
        return openhab.isConnected();
    }

    @Override
    public void close() throws Exception {
        openhab.close();
    }
}
