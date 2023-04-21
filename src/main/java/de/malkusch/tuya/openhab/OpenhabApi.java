package de.malkusch.tuya.openhab;

import static de.malkusch.tuya.openhab.OpenhabApi.Power.OFF;
import static de.malkusch.tuya.openhab.OpenhabApi.Power.ON;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.Thread.currentThread;
import static java.time.Duration.between;
import static java.time.Instant.now;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.smarthomej.binding.tuya.internal.local.DeviceStatusListener;
import org.smarthomej.binding.tuya.internal.local.TuyaDevice;

import de.malkusch.tuya.TuyaApi;
import lombok.extern.slf4j.Slf4j;

@Slf4j
final class OpenhabApi implements TuyaApi, AutoCloseable {

    final TuyaDevice device;
    private final Duration timeout;
    private final Duration expiration;

    static enum Power {
        ON, OFF;
    }

    static record State(Power power, Instant time) {
    }

    private volatile State state = new State(Power.OFF, Instant.MIN);
    private volatile Instant expireAt = Instant.MIN;

    OpenhabApi(TuyaDevice tuyaDevice, StateUpdater updater, Duration timeout, Duration expiration) throws IOException {
        this.device = tuyaDevice;
        this.timeout = timeout;
        this.expiration = expiration;

        synchronized (statusLock) {
            log.debug("Enable API");
            updater.enable(this);
            waitForState();
            if (isExpired()) {
                throw new IllegalStateException("Initial state wasn't received");
            }
        }
    }

    @Override
    public void turnOn() throws IOException {
        update(ON);
    }

    @Override
    public void turnOff() throws IOException {
        update(OFF);
    }

    @Override
    public boolean isOn() throws IOException {
        return state().power() == ON;
    }

    @Override
    public boolean isOnline() {
        return true;
    }

    void expire() {
        expireAt = Instant.MIN;
    }

    private boolean isExpired() {
        var now = now();
        return now.isAfter(expireAt) || now.equals(expireAt);
    }

    public State state() throws IOException {
        synchronized (statusLock) {
            if (isExpired()) {
                log.debug("Refreshing expired state");
                device.refreshStatus();
                device.requestStatus();
                waitForState();
            }
            return state;
        }
    }

    public void update(Power power) throws IOException {
        synchronized (statusLock) {
            var command = command(power);
            log.debug("Update power {}", command);
            expire();
            device.set(command);
            waitForState();
            if (state.power != power) {
                throw new IOException("Update power to failed");
            }
        }
    }

    void waitForState() throws IOException {
        var start = now();
        var waitUntil = start.plus(timeout);
        synchronized (statusLock) {
            if (!isExpired()) {
                return;
            }
            log.debug("Waiting for state");
            while (isExpired()) {

                if (now().isAfter(waitUntil)) {
                    throw new IOException(
                            "Waiting for state timed out after " + between(start, now()).toMillis() + " ms");
                }
                if (Thread.interrupted()) {
                    currentThread().interrupt();
                    throw new IOException("Waiting for state was interrupted");
                }

                try {
                    var waitMillis = waitMillis(waitUntil);
                    statusLock.wait(waitMillis);

                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    throw new IOException("Waiting for state was interrupted", e);
                }
            }
            log.debug("Waited {} ms", between(start, now()).toMillis());
        }
    }

    private static final long MAX_WAIT_MILLIS = 200;
    private static final long MIN_WAIT_MILLIS = 10;

    private static long waitMillis(Instant waitUntil) {
        var millisUntil = max(MIN_WAIT_MILLIS, between(now(), waitUntil).toMillis());
        return min(MAX_WAIT_MILLIS, millisUntil);
    }

    private final Object statusLock = new Object();

    static class StateUpdater implements DeviceStatusListener {

        private volatile OpenhabApi api;
        private final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void processDeviceStatus(Map<Integer, Object> deviceStatus) {
            log.debug("Received device status: {}", deviceStatus);
            waitUntilEnabled();
            api.updateDeviceStatus(deviceStatus);
        }

        private void waitUntilEnabled() {
            try {
                if (api == null) {
                    log.debug("Wait until api was enabled");
                    latch.await();
                    log.debug("Api was enabled");
                }

            } catch (InterruptedException e) {
                currentThread().interrupt();
                throw new RuntimeException("Initialization was interrupted", e);
            }
        }

        private void enable(OpenhabApi api) {
            this.api = api;
            latch.countDown();
        }

        @Override
        public void connectionStatus(boolean status) {
        }
    }

    private void updateDeviceStatus(Map<Integer, Object> deviceStatus) {
        synchronized (statusLock) {
            if (deviceStatus.get(1) instanceof Boolean on) {
                state = new State(on ? Power.ON : Power.OFF, now());
                log.debug("Update state: {}", state);
                expireAt = state.time.plus(expiration);
            }
            statusLock.notifyAll();
        }
    }

    private static Map<Integer, Object> command(Power power) {
        return Map.of(1, power == Power.ON);
    }

    @Override
    public void close() throws Exception {
        device.dispose();
    }
}
