package br.edu.ffb.pedro.bullyelectionp2p.payload;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

@JsonObject
public class Payload {
    @JsonField
    public String type;

    public Payload() {
    }

    public Payload(String type) {
        this.type = type;
    }
}
