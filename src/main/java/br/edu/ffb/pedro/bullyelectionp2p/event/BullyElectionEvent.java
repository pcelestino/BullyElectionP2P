package br.edu.ffb.pedro.bullyelectionp2p.event;

import br.edu.ffb.pedro.bullyelectionp2p.BullyElectionP2pDevice;

public class BullyElectionEvent {

    public static final String ELECTED_LEADER = "ELECTED_LEADER";

    public final String event;
    public BullyElectionP2pDevice device;

    public BullyElectionEvent(String event) {
        this.event = event;
    }

    public BullyElectionEvent(String event, BullyElectionP2pDevice device) {
        this.event = event;
        this.device = device;
    }
}
