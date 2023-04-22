package de.malkusch.tuya.openhab.api;

import static java.util.concurrent.Executors.newSingleThreadScheduledExecutor;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.openhab.core.util.HexUtils;

import com.google.gson.Gson;

public class DeviceTest {

    @Test
    void shouldDiscover() throws Exception {
        String deviceId = "bf3f795ce962dadfd88hx5";
        String localKey = "secret";
        var discoveryPacket = "000055AA00000000000000130000009C00000000D09766676F3369EB10B5E9F132FD802A7A1E40D0CBEBCBBAAF6D9037D72ADB12C0E08B85428F69C0F5EB443B3428B648A46ECF78D163C8B2F111C8C375148D32C2D91FEA632B8557EB918162C1FC96744DC7FE823E307927FB0A44B0DFAE2C8DA2A472BD72B20E15E0F77B889F46B89A20780716567CE38AC1D583E9669FE8F0EEDD5C953BB9377560AE90DFBC45DBE2F9C05DDF0000AA55";
        var factory = new Device.Factory(new Gson(), Duration.ofSeconds(3));

        sendPacket(discoveryPacket, DISCOVERY_PORT);
        var device = factory.device(deviceId, localKey);

        assertEquals("bf3f795ce962dadfd88hx5", device.id);
    }

    private final static int DISCOVERY_PORT = 6667;

    private static void sendPacket(String message, int port) {
        var packet = HexUtils.hexToBytes(message);
        newSingleThreadScheduledExecutor().schedule(() -> {
            try {
                var udpPacket = new DatagramPacket(packet, packet.length, InetAddress.getByName("127.0.0.1"), port);
                try (var datagramSocket = new DatagramSocket()) {
                    datagramSocket.send(udpPacket);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        }, 1, SECONDS);
    }

}
