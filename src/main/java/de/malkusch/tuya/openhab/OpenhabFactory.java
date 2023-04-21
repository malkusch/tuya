package de.malkusch.tuya.openhab;

import static java.lang.Thread.currentThread;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.smarthomej.binding.tuya.internal.local.DeviceInfoSubscriber;
import org.smarthomej.binding.tuya.internal.local.TuyaDevice;
import org.smarthomej.binding.tuya.internal.local.UdpDiscoveryListener;
import org.smarthomej.binding.tuya.internal.local.dto.DeviceInfo;

import com.google.gson.Gson;

import de.malkusch.tuya.TuyaApi;
import de.malkusch.tuya.TuyaApi.Factory;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class OpenhabFactory implements Factory {

    private final Gson gson;
    private final EventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    private final Duration timeout;
    private final Duration discoveryTimeout;
    private final Duration expiration;

    private final static Duration DEFAULT_EXPIRATION = Duration.ofMinutes(5);
    private final static Duration DEFAULT_DISCOVERY_TIMEOUT = Duration.ofSeconds(10);

    public OpenhabFactory(Duration timeout) {
        this(new Gson(), DEFAULT_DISCOVERY_TIMEOUT, timeout, DEFAULT_EXPIRATION);
    }

    public OpenhabFactory(Gson gson, Duration discoveryTimeout, Duration timeout, Duration expiration) {
        this.gson = gson;
        this.discoveryTimeout = discoveryTimeout;
        this.timeout = timeout;
        this.expiration = expiration;
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
                    latch.await(discoveryTimeout.toMillis(), MILLISECONDS);

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
    public TuyaApi newApi(String deviceId, String localKey) throws IOException {
        log.debug("Build Tuya API for device {}", deviceId);
        var discovery = new Discovery();
        var deviceInfo = discovery.discover(deviceId);

        var stateUpdater = new OpenhabApi.StateUpdater();
        var reconnectUpdated = new ReconnectingTuyaApi.ReconnectListener(stateUpdater);

        var device = new TuyaDevice(gson, reconnectUpdated, eventLoopGroup, deviceId, localKey.getBytes(UTF_8),
                deviceInfo.ip, deviceInfo.protocolVersion);

        var api = new OpenhabApi(device, stateUpdater, timeout, expiration);
        var reconnecting = new ReconnectingTuyaApi(api, timeout, reconnectUpdated);

        return reconnecting;
    }

    @Override
    public void close() throws Exception {
        eventLoopGroup.shutdownGracefully().await(timeout.toMillis());
    }
}
