package de.malkusch.tuya.openhab.api;

import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.smarthomej.binding.tuya.internal.local.DeviceInfoSubscriber;
import org.smarthomej.binding.tuya.internal.local.TuyaDevice;
import org.smarthomej.binding.tuya.internal.local.UdpDiscoveryListener;
import org.smarthomej.binding.tuya.internal.local.dto.DeviceInfo;

import com.google.gson.Gson;

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class Device extends TuyaDevice implements AutoCloseable {

    final ApiSync sync;
    private final InetAddress address;
    final String id;

    private Device(Gson gson, ApiSync sync, EventLoopGroup eventLoopGroup, String deviceId, byte[] deviceKey,
            String address, String protocolVersion) throws UnknownHostException {

        super(gson, sync, eventLoopGroup, deviceId, deviceKey, address, protocolVersion);

        this.sync = sync;
        this.id = deviceId;
        this.address = InetAddress.getByName(address);
    }

    public void checkConnected(Duration timeout) {
        try {
            if (!address.isReachable((int) timeout.toMillis())) {
                log.debug("Disconnecting");
                sync.connectionStatus(false);
            }
        } catch (IOException e) {
            log.debug("Disconnecting");
            sync.connectionStatus(false);
        }
    }

    @RequiredArgsConstructor
    @Slf4j
    static public final class Factory implements AutoCloseable {

        private final Gson gson;
        private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
        private final Duration timeout;

        public Device device(String deviceId, String localKey) throws IOException {
            var discovery = new Discovery();
            var deviceInfo = discovery.discover(deviceId);

            var sync = new ApiSync();
            var device = new Device(gson, sync, eventLoopGroup, deviceId, localKey.getBytes(UTF_8), deviceInfo.ip,
                    deviceInfo.protocolVersion);

            return device;
        }

        private class Discovery implements DeviceInfoSubscriber {

            private volatile DeviceInfo deviceInfo;
            private final CountDownLatch latch = new CountDownLatch(1);

            @Override
            public void deviceInfoChanged(DeviceInfo deviceInfo) {
                log.debug("Discovered {}", deviceInfo);
                this.deviceInfo = deviceInfo;
                latch.countDown();
            }

            public DeviceInfo discover(String deviceId) throws IOException {
                var listener = new UdpDiscoveryListener(eventLoopGroup);
                try {
                    listener.registerListener(deviceId, this);
                    try {
                        latch.await(timeout.toMillis(), MILLISECONDS);

                    } catch (InterruptedException e) {
                        currentThread().interrupt();
                        throw new IOException("Discovery was interrupted", e);
                    }

                    if (deviceInfo == null) {
                        throw new IOException("Discovery timed out");
                    }
                    return deviceInfo;

                } finally {
                    listener.unregisterListener(this);
                    listener.deactivate();
                }
            }
        }

        @Override
        public void close() throws Exception {
            eventLoopGroup.shutdownGracefully().await(timeout.toMillis());
        }
    }

    @Override
    public void close() throws Exception {
        dispose();
    }
}
