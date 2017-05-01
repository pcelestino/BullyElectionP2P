package br.edu.ffb.pedro.bullyelectionp2p.event;

public class ClientEvent {

    public static final String REGISTERED = "REGISTERED";
    public static final String UNREGISTERED = "UNREGISTERED";
    public static final String REGISTRATION_FAIL = "REGISTRATION_FAIL";
    public static final String UNREGISTRATION_FAIL = "UNREGISTRATION_FAIL";

    public final String event;

    public ClientEvent(String event) {
        this.event = event;
    }
}
