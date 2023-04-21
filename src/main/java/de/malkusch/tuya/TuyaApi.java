package de.malkusch.tuya;

import java.io.IOException;
import java.time.Duration;

import de.malkusch.tuya.openhab.OpenhabFactory;

public interface TuyaApi extends AutoCloseable {

    final static Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    static Factory newFactory() {
        return new OpenhabFactory(DEFAULT_TIMEOUT);
    }

    static Factory newFactory(Duration timeout) {
        return new OpenhabFactory(timeout);
    }

    public interface Factory extends AutoCloseable {
        TuyaApi newApi(String deviceId, String localKey) throws IOException;
    }

    void turnOn() throws IOException;

    void turnOff() throws IOException;

    boolean isOn() throws IOException;

    boolean isOnline();
}
