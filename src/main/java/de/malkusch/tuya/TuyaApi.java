package de.malkusch.tuya;

import java.io.IOException;
import java.time.Duration;

import de.malkusch.tuya.openhab.OpenhabTuyaApi;

public interface TuyaApi extends AutoCloseable {
    public interface Factory extends AutoCloseable {
        public static class Builder {
            private final static Duration DEFAULT_DEVICE_TIMEOUT = Duration.ofSeconds(10);
            private final static Duration DEFAULT_DISCOVERY_TIMEOUT = Duration.ofSeconds(10);
            private final static Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);

            private Duration deviceTimeout = DEFAULT_DEVICE_TIMEOUT;
            private Duration discoveryTimeout = DEFAULT_DISCOVERY_TIMEOUT;
            private Duration expiration = DEFAULT_EXPIRATION;

            public Builder withDeviceTimeout(Duration deviceTimeout) {
                this.deviceTimeout = deviceTimeout;
                return this;
            }

            public Builder withDiscoveryTimeout(Duration discoveryTimeout) {
                this.discoveryTimeout = discoveryTimeout;
                return this;
            }

            public Builder withExpiration(Duration expiration) {
                this.expiration = expiration;
                return this;
            }

            public Factory factory() {
                return new OpenhabTuyaApi.Factory(deviceTimeout, discoveryTimeout, expiration);
            }
        }

        TuyaApi api(String deviceId, String localKey) throws IOException;
    }

    public static Factory.Builder buildFactory() {
        return new Factory.Builder();
    }

    void turnOn() throws IOException;

    void turnOff() throws IOException;

    boolean isOn() throws IOException;

    boolean isOnline();
}
