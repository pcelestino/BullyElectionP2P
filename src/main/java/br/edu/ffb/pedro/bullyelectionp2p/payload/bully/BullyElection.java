package br.edu.ffb.pedro.bullyelectionp2p.payload.bully;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import br.edu.ffb.pedro.bullyelectionp2p.BullyElectionP2pDevice;
import br.edu.ffb.pedro.bullyelectionp2p.payload.Payload;

@SuppressWarnings("WeakerAccess")
@JsonObject
public class BullyElection extends Payload {

    @JsonIgnore
    public static final String TYPE = "BullyElection";
    @JsonIgnore
    public static final int TIMEOUT = 10000;
    @JsonIgnore
    public static boolean ongoingElection = false;
    @JsonIgnore
    public static boolean hasElectionResponse = false;


    // Mensagens
    @JsonIgnore
    public static final String START_ELECTION = "startElection";
    @JsonIgnore
    public static final String RESPOND_OK = "respondOk";
    @JsonIgnore
    public static final String INFORM_LEADER = "informLeader";

    @JsonField
    public String message;
    @JsonField
    public BullyElectionP2pDevice device;

    public BullyElection() {
        super(TYPE);
    }

    public BullyElection(String message) {
        super(TYPE);
        this.message = message;
    }

    public BullyElection(String message, BullyElectionP2pDevice device) {
        super(TYPE);
        this.message = message;
        this.device = device;
    }
}
