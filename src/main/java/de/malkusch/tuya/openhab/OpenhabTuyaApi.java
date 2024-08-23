package de.malkusch.tuya.openhab;

import com.google.gson.Gson;
import de.malkusch.tuya.TuyaApi;
import de.malkusch.tuya.openhab.api.Api;
import de.malkusch.tuya.openhab.api.Api.Power;
import de.malkusch.tuya.openhab.api.Device;

import java.io.IOException;
import java.time.Duration;

import static java.util.Objects.requireNonNull;

public final class OpenhabTuyaApi implements TuyaApi {

    public final static class Factory implements TuyaApi.Factory {

        private final Api.Factory apiFactory;
        private final Device.Factory deviceFactory;

        public Factory(Duration deviceTimout, Duration discoveryTime, Duration expiration) {
            this(new Gson(), discoveryTime, deviceTimout, expiration);
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

    public OpenhabTuyaApi(Api openhab) {
        this.openhab = requireNonNull(openhab);
    }

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
