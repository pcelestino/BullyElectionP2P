package br.edu.ffb.pedro.bullyelectionp2p.event;

import br.edu.ffb.pedro.bullyelectionp2p.BullyElectionP2pDevice;

public class WifiP2pEvent {

    public static final String WIFI_P2P_ENABLED = "WIFI_P2P_ENABLED";
    public static final String WIFI_P2P_DISABLED = "WIFI_P2P_DISABLED";
    public static final String CONNECTED_TO_ANOTHER_DEVICE = "CONNECTED_TO_ANOTHER_DEVICE";
    public static final String DISCONNECTED_FROM_ANOTHER_DEVICE = "DISCONNECTED_FROM_ANOTHER_DEVICE";
    public static final String THIS_DEVICE_CHANGED = "THIS_DEVICE_CHANGED";
    public static final String SERVER_DEVICE_FOUND = "SERVER_DEVICE_FOUND";
    public static final String P2P_UNSUPPORTED = "P2P_UNSUPPORTED";
    public static final String TIMEOUT = "TIMEOUT";
    public static final String ERROR = "ERROR";

    public final String event;
    public BullyElectionP2pDevice device;

    public WifiP2pEvent(String event) {
        this.event = event;
    }

    public WifiP2pEvent(String event, BullyElectionP2pDevice device) {
        this.event = event;
        this.device = device;
    }
}
