package de.malkusch.tuya.openhab;

import static java.lang.Thread.currentThread;
import static java.time.Instant.now;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

import org.smarthomej.binding.tuya.internal.local.DeviceStatusListener;

import de.malkusch.tuya.TuyaApi;

final class ReconnectingTuyaApi implements TuyaApi, AutoCloseable {

    private final OpenhabApi api;
    private final Duration timeout;

    public ReconnectingTuyaApi(OpenhabApi api, Duration timeout, ReconnectListener listener) {
        this.api = api;
        this.timeout = timeout;

        listener.api = this;
    }

    static final class ReconnectListener implements DeviceStatusListener {

        private final DeviceStatusListener listener;
        private volatile ReconnectingTuyaApi api;

        ReconnectListener(DeviceStatusListener listener) {
            this.listener = listener;
        }

        @Override
        public void processDeviceStatus(Map<Integer, Object> deviceStatus) {
            listener.processDeviceStatus(deviceStatus);
        }

        @Override
        public void connectionStatus(boolean status) {
            listener.connectionStatus(status);
            if (api != null) {
                api.updateConnected(status);
            }
        }
    }

    private volatile boolean connected = true;
    private final Object updateLock = new Object();

    void updateConnected(boolean connected) {
        synchronized (updateLock) {
            this.connected = connected;
            updateLock.notifyAll();
        }
    }

    private boolean awaitConnected() {
        var start = now();
        var waitUntil = start.plus(timeout);
        synchronized (updateLock) {
            while (!connected) {
                if (Thread.interrupted()) {
                    currentThread().interrupt();
                    return connected;
                }
                if (now().isAfter(waitUntil)) {
                    return connected;
                }
                try {
                    updateLock.wait(100);

                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    return connected;
                }
            }
        }
        return connected;
    }

    @Override
    public void turnOn() throws IOException {
        reconnected(api::turnOn);
    }

    @Override
    public void turnOff() throws IOException {
        reconnected(api::turnOff);
    }

    private volatile boolean lastOn = false;

    @Override
    public boolean isOn() throws IOException {
        lastOn = reconnected(api::isOn, lastOn);
        return lastOn;
    }

    @Override
    public boolean isOnline() {
        return connected && api.isOnline();
    }

    private static interface Query<T, E extends Exception> {
        T query() throws E;
    }

    private <T, E extends Exception> T reconnected(Query<T, E> query, T fallback) throws E {
        if (!connected) {
            api.device.dispose();
            api.device.connect();
            if (!awaitConnected()) {
                api.device.dispose();
                return fallback;
            }
        }
        return query.query();
    }

    private static interface Command<E extends Exception> {
        void execute() throws E;
    }

    private <E extends Exception> void reconnected(Command<E> command) throws E {
        Query<Void, E> query = () -> {
            command.execute();
            return null;
        };
        reconnected(query, null);
    }

    @Override
    public void close() throws Exception {
        api.close();
    }
}
