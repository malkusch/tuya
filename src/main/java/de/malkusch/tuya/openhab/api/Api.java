package de.malkusch.tuya.openhab.api;

import static java.time.Instant.now;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import lombok.RequiredArgsConstructor;

public interface Api extends AutoCloseable {

    @RequiredArgsConstructor
    public static final class Factory {
        private final Duration timeout;
        private final Duration expiration;

        public Api api(Device device) throws IOException {
            var deviceApi = new DeviceApi(device, timeout, expiration);
            var reconnectedApi = new ReconnectingApi(deviceApi, timeout);

            var api = reconnectedApi;
            device.sync.enable(api);

            return api;
        }
    }

    static enum Power {
        ON, OFF;

        Map<Integer, Object> command() {
            return Map.of(1, this == Power.ON);
        }
    }

    static record State(Power power, Instant time) {
        static Optional<State> fromDeviceStatus(Map<Integer, Object> deviceStatus) {
            if (deviceStatus.get(1) instanceof Boolean on) {
                return Optional.of(new State(on ? Power.ON : Power.OFF, now()));
            }
            return Optional.empty();
        }
    }

    void enable() throws IOException;

    State state() throws IOException;

    void syncState(State state);

    void send(Power power) throws IOException;

    void syncConnected(boolean connected);

    boolean isConnected();

    Device device();
}
