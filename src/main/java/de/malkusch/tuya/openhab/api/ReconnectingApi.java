package de.malkusch.tuya.openhab.api;

import java.io.IOException;
import java.time.Duration;

import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.Thread.currentThread;
import static java.time.Duration.between;
import static java.time.Instant.now;
import static java.util.Objects.requireNonNull;

final class ReconnectingApi implements Api {

    private static final System.Logger log = System.getLogger(ApiSync.class.getName());
    private final Api api;
    private final Duration timeout;
    private final Object lock = new Object();

    public ReconnectingApi(Api api, Duration timeout) {
        this.api = requireNonNull(api);
        this.timeout = requireNonNull(timeout);
    }

    @Override
    public void enable() throws IOException {
        api.enable();
        synchronized (lock) {
            if (!awaitConnected()) {
                throw new IOException("Not connected");
            }
        }
    }

    @Override
    public State state() throws IOException {
        return reconnected(api::state);
    }

    @Override
    public void syncState(State state) {
        api.syncState(state);
    }

    @Override
    public void send(Power power) throws IOException {
        reconnected(() -> api.send(power));
    }

    @Override
    public void syncConnected(boolean connected) {
        synchronized (lock) {
            api.syncConnected(connected);
            lock.notifyAll();
        }
    }

    @Override
    public boolean isConnected() {
        return api.isConnected();
    }

    private static interface Query<T> {
        T query() throws IOException;
    }

    private <T> T reconnected(Query<T> query) throws IOException {
        synchronized (lock) {
            if (!isConnected()) {
                log.log(DEBUG, "Reconnecting");
                device().dispose();
                device().connect();
                if (!awaitConnected()) {
                    log.log(DEBUG, "Reconnecting failed");
                    device().dispose();
                    throw new IOException("Reconnect failed");
                }
                log.log(DEBUG, "Reconnected");
            }
        }
        try {
            return query.query();

        } catch (IOException e) {
            checkConnected();
            throw e;
        }
    }

    private void checkConnected() {
        synchronized (lock) {
            if (!isConnected()) {
                return;
            }
            device().checkConnected(timeout);
        }
    }

    private static interface Command {
        void execute() throws IOException;
    }

    private void reconnected(Command command) throws IOException {
        Query<Void> query = () -> {
            command.execute();
            return null;
        };
        reconnected(query);
    }

    private boolean awaitConnected() {
        var start = now();
        var waitUntil = start.plus(timeout);
        synchronized (lock) {
            if (isConnected()) {
                return true;
            }
            log.log(DEBUG, "Waiting for connection");
            while (!isConnected()) {
                if (Thread.interrupted()) {
                    currentThread().interrupt();
                    return isConnected();
                }
                if (now().isAfter(waitUntil)) {
                    log.log(DEBUG, "Waiting for connection timed out");
                    return isConnected();
                }
                try {
                    lock.wait(100);

                } catch (InterruptedException e) {
                    currentThread().interrupt();
                    return isConnected();
                }
            }
            log.log(DEBUG, "Connected after {0} ms", between(start, now()).toMillis());
        }
        return isConnected();
    }

    @Override
    public Device device() {
        return api.device();
    }

    @Override
    public void close() throws Exception {
        api.close();
    }
}
