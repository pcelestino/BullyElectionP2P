package br.edu.ffb.pedro.bullyelectionp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import br.edu.ffb.pedro.bullyelectionp2p.event.ClientEvent;
import br.edu.ffb.pedro.bullyelectionp2p.payload.bully.BullyElection;
import br.edu.ffb.pedro.bullyelectionp2p.payload.device.DeviceInfo;
import okio.BufferedSink;
import okio.Okio;

@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class BackgroundClientRegistrationJob implements AsyncJob.OnBackgroundJob {

    private BullyElectionP2p bullyElectionP2pInstance;
    private InetSocketAddress hostDeviceAddress;
    private final int BUFFER_SIZE = 65536;
    protected static boolean disableWiFiOnUnregister;

    public BackgroundClientRegistrationJob(BullyElectionP2p bullyElectionP2pInstance, InetSocketAddress hostDeviceAddress) {
        this.hostDeviceAddress = hostDeviceAddress;
        this.bullyElectionP2pInstance = bullyElectionP2pInstance;
    }

    @Override
    public void doOnBackground() {
        Log.d(BullyElectionP2p.TAG, "\nTentativa de transferência de dados de registro com o servidor...");
        Socket registrationSocket = new Socket();

        try {
            registrationSocket.connect(hostDeviceAddress, BullyElection.TIMEOUT);
            registrationSocket.setReceiveBufferSize(BUFFER_SIZE);
            registrationSocket.setSendBufferSize(BUFFER_SIZE);

            //Se esse código for alcançado, nós nos conectaremos ao servidor e transferiremos dados.
            Log.d(BullyElectionP2p.TAG, bullyElectionP2pInstance.thisDevice.deviceName + " Está conectado ao servidor, transferindo dados de registo...");

            BufferedSink toClient = Okio.buffer(Okio.sink(registrationSocket));

            Log.d(BullyElectionP2p.TAG, "Enviando dados de registro do cliente para o servidor...");
            String serializedClient = LoganSquare.serialize(bullyElectionP2pInstance.thisDevice);
            toClient.writeUtf8(serializedClient);
            toClient.flush();

            if (!bullyElectionP2pInstance.thisDevice.isRegistered) {
                Log.d(BullyElectionP2p.TAG, "Recebimento de dados de registro do servidor...");
                DeviceInfo serverDeviceInfo = LoganSquare.parse(registrationSocket.getInputStream(), DeviceInfo.class);

                serverDeviceInfo.device.serviceAddress = registrationSocket.getInetAddress().toString().replace("/", "");
                bullyElectionP2pInstance.thisDevice.serviceAddress = serverDeviceInfo.message;
                bullyElectionP2pInstance.registeredHost = serverDeviceInfo.device;
                bullyElectionP2pInstance.registeredClients = serverDeviceInfo.devices;

                Log.d(BullyElectionP2p.TAG, "Host Registrado | " + bullyElectionP2pInstance.registeredHost.deviceName);

                bullyElectionP2pInstance.thisDevice.isRegistered = true;
                EventBus.getDefault().post(new ClientEvent(ClientEvent.REGISTERED));

                // Informa a todos os outros dispositivos as informações do dispositivo atual
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = DeviceInfo.INFORM_DEVICE;
                deviceInfo.device = bullyElectionP2pInstance.thisDevice;
                bullyElectionP2pInstance.sendToAllDevices(deviceInfo);
                bullyElectionP2pInstance.startListeningForData();

            } else {

                DeviceInfo deviceInfo = LoganSquare.parse(registrationSocket.getInputStream(), DeviceInfo.class);
                Log.d(BullyElectionP2p.TAG, "Código de remoção: " + deviceInfo.message);

                bullyElectionP2pInstance.thisDevice.isRegistered = false;
                bullyElectionP2pInstance.registeredHost = null;
                bullyElectionP2pInstance.closeDataSocket();
                bullyElectionP2pInstance.disconnectFromDevice();

                // Remove o dispositivo de todos os dispositivos conectados na rede
                DeviceInfo removeInfo = new DeviceInfo();
                removeInfo.message = DeviceInfo.REMOVE_DEVICE;
                removeInfo.device = bullyElectionP2pInstance.thisDevice;
                bullyElectionP2pInstance.sendToAllDevices(removeInfo);

                EventBus.getDefault().post(new ClientEvent(ClientEvent.UNREGISTERED));
                Log.d(BullyElectionP2p.TAG, "Este dispositivo foi removido com êxito do servidor.");
            }

            toClient.close();

        } catch (IOException ex) {
            ex.printStackTrace();

            Log.e(BullyElectionP2p.TAG, "Ocorreu um erro ao tentar registrar ou cancelar o registro.");

            if (!bullyElectionP2pInstance.thisDevice.isRegistered) {
                EventBus.getDefault().post(new ClientEvent(ClientEvent.REGISTRATION_FAIL));
            } else {
                EventBus.getDefault().post(new ClientEvent(ClientEvent.UNREGISTRATION_FAIL));
            }

            if (bullyElectionP2pInstance.thisDevice.isRegistered && bullyElectionP2pInstance.isConnectedToAnotherDevice) {
                //Failed to unregister so an outright disconnect is necessary.
                bullyElectionP2pInstance.disconnectFromDevice();
            }
        } finally {
            if (disableWiFiOnUnregister) {
                BullyElectionP2p.disableWiFi(bullyElectionP2pInstance.context);
            }
            try {
                registrationSocket.close();
            } catch (Exception ex) {
                Log.e(BullyElectionP2p.TAG, "Falha ao fechar o soquete de registro.");
            }
        }
    }
}
