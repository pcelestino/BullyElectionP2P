package br.edu.ffb.pedro.bullyelectionp2p.payload.device;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonIgnore;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.ArrayList;

import br.edu.ffb.pedro.bullyelectionp2p.BullyElectionP2pDevice;
import br.edu.ffb.pedro.bullyelectionp2p.payload.Payload;

@SuppressWarnings("WeakerAccess")
@JsonObject
public class DeviceInfo extends Payload {

    @JsonIgnore
    public static final String TYPE = "DeviceInfo";

    // Mensagens
    @JsonIgnore
    public static final String INFORM_DEVICE = "informDevice";
    @JsonIgnore
    public static final String REMOVE_DEVICE = "removeDevice";

    @JsonField
    public String message;
    @JsonField
    public BullyElectionP2pDevice device;
    @JsonField
    public ArrayList<BullyElectionP2pDevice> devices;

    public DeviceInfo() {
        super(TYPE);
    }
}
