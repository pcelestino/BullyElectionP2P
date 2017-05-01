package br.edu.ffb.pedro.bullyelectionp2p;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.NetworkInfo;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pManager;
import android.util.Log;

import org.greenrobot.eventbus.EventBus;

import br.edu.ffb.pedro.bullyelectionp2p.event.WifiP2pEvent;

public class BullyElectionP2pBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        switch (intent.getAction()) {
            case WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION:
                // Determina se o Wifi P2P está habilitado ou não
                switch (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)) {
                    case WifiP2pManager.WIFI_P2P_STATE_ENABLED:
                        EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.WIFI_P2P_ENABLED));
                        break;
                    default:
                        EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.WIFI_P2P_DISABLED));
                }
                break;
            case WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION:
                NetworkInfo networkInfo = intent.getParcelableExtra(WifiP2pManager.EXTRA_NETWORK_INFO);
                if (networkInfo.isConnected() && networkInfo.getTypeName().equals("WIFI_P2P")) {
                    EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.CONNECTED_TO_ANOTHER_DEVICE));
                } else {
                    EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.DISCONNECTED_FROM_ANOTHER_DEVICE));
                }
                break;
            case WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION:
                WifiP2pDevice device = intent.getParcelableExtra(WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                Log.d(BullyElectionP2p.TAG, "Status do dispositivo - " + getDeviceStatus(device.status));
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.THIS_DEVICE_CHANGED, new BullyElectionP2pDevice(device)));
                break;
        }
    }

    private String getDeviceStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pDevice.AVAILABLE:
                return "Disponível";
            case WifiP2pDevice.INVITED:
                return "Convidado";
            case WifiP2pDevice.CONNECTED:
                return "Conectado";
            case WifiP2pDevice.FAILED:
                return "Falhou";
            case WifiP2pDevice.UNAVAILABLE:
                return "Indisponível";
            default:
                return "Desconhecido";
        }
    }
}
