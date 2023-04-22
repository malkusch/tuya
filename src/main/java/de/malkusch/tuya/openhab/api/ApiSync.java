package de.malkusch.tuya.openhab.api;

import static de.malkusch.tuya.openhab.api.Api.State.fromDeviceStatus;
import static java.lang.Thread.currentThread;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.smarthomej.binding.tuya.internal.local.DeviceStatusListener;

import lombok.extern.slf4j.Slf4j;

@Slf4j
final class ApiSync implements DeviceStatusListener {

    private volatile Api api;

    @Override
    public void processDeviceStatus(Map<Integer, Object> deviceStatus) {
        log.debug("Received device status: {}", deviceStatus);
        waitUntilEnabled();
        fromDeviceStatus(deviceStatus).ifPresent(api::syncState);
    }

    @Override
    public void connectionStatus(boolean status) {
        log.debug("Received connection status: {}", status);
        waitUntilEnabled();
        api.syncConnected(status);
    }

    private final CountDownLatch enableLatch = new CountDownLatch(1);

    void enable(Api api) throws IOException {
        log.debug("Enabling");
        this.api = api;
        enableLatch.countDown();

        api.enable();
    }

    private void waitUntilEnabled() {
        try {
            if (api == null) {
                log.debug("Wait until enabled");
                enableLatch.await();
                log.debug("Enabled");
            }

        } catch (InterruptedException e) {
            currentThread().interrupt();
            throw new RuntimeException("Initialization was interrupted", e);
        }
    }
}