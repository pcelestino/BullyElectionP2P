package br.edu.ffb.pedro.bullyelectionp2p.event;

public class DataTransferEvent {

    public static final String DATA_RECEIVED = "DATA_RECEIVED";
    public static final String SENT = "SENT";
    public static final String FAILURE = "FAILURE";

    public final String event;
    public String data;

    public DataTransferEvent(String event) {
        this.event = event;
    }

    public DataTransferEvent(String event, String data) {
        this.event = event;
        this.data = data;
    }
}
