package de.malkusch.tuya.openhab.api;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import static java.lang.Math.max;
import static java.lang.Math.min;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.Thread.currentThread;
import static java.time.Duration.between;
import static java.time.Instant.now;

final class DeviceApi implements Api {

    private static final System.Logger log = System.getLogger(DeviceApi.class.getName());
    private final Device device;
    private final Duration timeout;
    private final Duration expiration;
    private final Object lock = new Object();

    private volatile State state = new State(Power.OFF, Instant.MIN);
    private volatile boolean connected = false;
    private volatile Instant expireAt = Instant.MIN;

    DeviceApi(Device device, Duration timeout, Duration expiration) throws IOException {
        this.device = device;
        this.timeout = timeout;
        this.expiration = expiration;
    }

    @Override
    public void enable() throws IOException {
        synchronized (lock) {
            waitForState();
            if (isExpired()) {
                throw new IllegalStateException("State wasn't received");
            }
        }
    }

    @Override
    public State state() throws IOException {
        synchronized (lock) {
            if (isExpired()) {
                log.log(DEBUG, "Requesting expired state");
                // device.refreshStatus();
                device.requestStatus();
                waitForState();
            }
            return state;
        }
    }

    @Override
    public void syncState(State state) {
        synchronized (lock) {
            this.state = state;
            expireAt = state.time().plus(expiration);
            lock.notifyAll();
        }
    }

    @Override
    public void send(Power power) throws IOException {
        synchronized (lock) {
            var command = command(power);
            log.log(DEBUG, "Update power {0}", command);
            expire();
            device.set(command);
            waitForState();
            if (state.power() != power) {
                throw new IOException("Update power didn't change the state");
            }
        }
    }

    private static Map<Integer, Object> command(Power power) {
        return Map.of(1, power == Power.ON);
    }

    @Override
    public void syncConnected(boolean connected) {
        this.connected = connected;
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public Device device() {
        return device;
    }

    private void expire() {
        expireAt = Instant.MIN;
    }

    private boolean isExpired() {
        var now = now();
        return now.isAfter(expireAt) || now.equals(expireAt);
    }

    void waitForState() throws IOException {
        var start = now();
        var waitUntil = start.plus(timeout);
        synchronized (lock) {
            if (!isExpired()) {
                return;
            }
            log.log(DEBUG, "Waiting for state");
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
                    lock.wait(waitMillis);

                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    throw new IOException("Waiting for state was interrupted", e);
                }
            }
            log.log(DEBUG, "Waited {0} ms", between(start, now()).toMillis());
        }
    }

    private static final long MAX_WAIT_MILLIS = 200;
    private static final long MIN_WAIT_MILLIS = 10;

    private static long waitMillis(Instant waitUntil) {
        var millisUntil = max(MIN_WAIT_MILLIS, between(now(), waitUntil).toMillis());
        return min(MAX_WAIT_MILLIS, millisUntil);
    }

    @Override
    public void close() throws Exception {
        device.close();
    }
}
