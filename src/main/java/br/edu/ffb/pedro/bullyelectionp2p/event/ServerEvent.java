package br.edu.ffb.pedro.bullyelectionp2p.event;

import br.edu.ffb.pedro.bullyelectionp2p.BullyElectionP2pDevice;

public class ServerEvent {

    public static final String DEVICE_REGISTERED_WITH_HOST = "DEVICE_REGISTERED_WITH_HOST";
    public static final String DEVICE_UNREGISTERED_WITH_HOST = "DEVICE_UNREGISTERED_WITH_HOST";
    public static final String SERVER_CLOSED = "SERVER_CLOSED";

    public final String event;
    public final BullyElectionP2pDevice device;

    public ServerEvent(String event, BullyElectionP2pDevice device) {
        this.event = event;
        this.device = device;
    }
}
