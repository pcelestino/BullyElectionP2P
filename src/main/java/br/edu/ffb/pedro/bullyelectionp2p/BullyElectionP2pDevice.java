package br.edu.ffb.pedro.bullyelectionp2p;

import android.net.wifi.p2p.WifiP2pDevice;

import com.bluelinelabs.logansquare.annotation.JsonField;
import com.bluelinelabs.logansquare.annotation.JsonObject;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("WeakerAccess")
@JsonObject
public class BullyElectionP2pDevice {

    @JsonField
    public long id;
    @JsonField
    public String deviceName;
    @JsonField
    public String serviceName;
    @JsonField
    public String readableName;
    @JsonField
    public boolean isRegistered;
    @JsonField
    public boolean isLeader;
    @JsonField
    protected int serverPort;
    @JsonField
    protected int servicePort;
    @JsonField
    protected String serviceType;
    @JsonField
    protected String macAddress;
    @JsonField
    protected String serviceAddress;
    @JsonField
    protected boolean isHost;

    public BullyElectionP2pDevice() {
    }

    public BullyElectionP2pDevice(WifiP2pDevice device) {
        this.deviceName = device.deviceName;
        this.macAddress = device.deviceAddress;
    }

    public BullyElectionP2pDevice(WifiP2pDevice device, Map<String, String> txtRecord) {
        this.serviceName = txtRecord.get("SERVICE_NAME");
        this.readableName = txtRecord.get("READABLE_NAME");
        this.deviceName = device.deviceName;
        this.macAddress = device.deviceAddress;
    }

    public Map<String, String> getTxtRecord() {
        return new HashMap<String, String>() {{
            put("SERVICE_NAME", serviceName);
            put("READABLE_NAME", readableName);
        }};
    }

    @Override
    public String toString() {
        return "BullyElectionP2pDevice{" +
                "id=" + id +
                ", deviceName='" + deviceName + '\'' +
                ", serviceName='" + serviceName + '\'' +
                ", readableName='" + readableName + '\'' +
                ", isRegistered=" + isRegistered +
                ", isLeader=" + isLeader +
                ", serverPort=" + serverPort +
                ", servicePort=" + servicePort +
                ", serviceType='" + serviceType + '\'' +
                ", macAddress='" + macAddress + '\'' +
                ", serviceAddress='" + serviceAddress + '\'' +
                ", isHost=" + isHost +
                '}';
    }
}
