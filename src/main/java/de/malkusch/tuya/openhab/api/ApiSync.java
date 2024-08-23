package de.malkusch.tuya.openhab.api;

import org.smarthomej.binding.tuya.internal.local.DeviceStatusListener;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import static de.malkusch.tuya.openhab.api.Api.State.fromDeviceStatus;
import static java.lang.System.Logger.Level.DEBUG;
import static java.lang.Thread.currentThread;

final class ApiSync implements DeviceStatusListener {

    private static final System.Logger log = System.getLogger(ApiSync.class.getName());
    private volatile Api api;

    @Override
    public void processDeviceStatus(Map<Integer, Object> deviceStatus) {
        log.log(DEBUG, "Received device status: {0}", deviceStatus);
        waitUntilEnabled();
        fromDeviceStatus(deviceStatus).ifPresent(api::syncState);
    }

    @Override
    public void connectionStatus(boolean status) {
        log.log(DEBUG, "Received connection status: {0}", status);
        waitUntilEnabled();
        api.syncConnected(status);
    }

    private final CountDownLatch enableLatch = new CountDownLatch(1);

    void enable(Api api) throws IOException {
        log.log(DEBUG, "Enabling");
        this.api = api;
        enableLatch.countDown();

        api.enable();
    }

    private void waitUntilEnabled() {
        try {
            if (api == null) {
                log.log(DEBUG, "Wait until enabled");
                enableLatch.await();
                log.log(DEBUG, "Enabled");
            }

        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException("Initialization was interrupted", e);
        }
    }
}