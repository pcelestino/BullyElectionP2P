package br.edu.ffb.pedro.bullyelectionp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import org.greenrobot.eventbus.EventBus;

import java.net.Socket;
import java.util.Iterator;

import br.edu.ffb.pedro.bullyelectionp2p.event.ServerEvent;
import br.edu.ffb.pedro.bullyelectionp2p.payload.device.DeviceInfo;
import okio.BufferedSink;
import okio.Okio;

@SuppressWarnings("WeakerAccess")
public class BackgroundServerRegistrationJob implements AsyncJob.OnBackgroundJob {

    private BullyElectionP2p bullyElectionP2pInstance;
    private Socket clientSocket;

    public BackgroundServerRegistrationJob(BullyElectionP2p bullyElectionP2pInstance, Socket clientSocket) {
        this.clientSocket = clientSocket;
        this.bullyElectionP2pInstance = bullyElectionP2pInstance;
    }

    @Override
    public void doOnBackground() {
        try {
            //Se esse código for atingido, um cliente conectou e transferiu dados.
            Log.d(BullyElectionP2p.TAG, "Um dispositivo está conectado ao servidor, transferindo dados...");
            BufferedSink toClient = Okio.buffer(Okio.sink(clientSocket));

            Log.d(BullyElectionP2p.TAG, "Recebimento de dados de registro do cliente...");
            BullyElectionP2pDevice clientDevice = LoganSquare.parse(clientSocket.getInputStream(), BullyElectionP2pDevice.class);
            clientDevice.serviceAddress = clientSocket.getInetAddress().toString().replace("/", "");
            bullyElectionP2pInstance.thisDevice.serviceAddress = clientDevice.serviceAddress;

            if (!clientDevice.isRegistered) {

                Log.d(BullyElectionP2p.TAG, "Enviando dados de registro do servidor: " + clientDevice);
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = clientDevice.serviceAddress;
                deviceInfo.device = bullyElectionP2pInstance.thisDevice;
                deviceInfo.devices = bullyElectionP2pInstance.registeredClients;

                String serializedServer = LoganSquare.serialize(deviceInfo);
                toClient.writeUtf8(serializedServer);
                toClient.flush();

                clientDevice.isRegistered = true;
                if (bullyElectionP2pInstance.registeredClients.isEmpty()) {
                    bullyElectionP2pInstance.startListeningForData();
                }
                bullyElectionP2pInstance.registeredClients.add(clientDevice);

                EventBus.getDefault().post(new ServerEvent(ServerEvent.DEVICE_REGISTERED_WITH_HOST, clientDevice));

            } else {
                Log.d(BullyElectionP2p.TAG, "Solicitação recebida para cancelar o registro do dispositivo");

                Log.d(BullyElectionP2p.TAG, "Enviando código de registro...");
                DeviceInfo deviceInfo = new DeviceInfo();
                deviceInfo.message = BullyElectionP2p.UNREGISTER_CODE;
                String serializedMessage = LoganSquare.serialize(deviceInfo);

                toClient.writeUtf8(serializedMessage);
                toClient.flush();

                Log.d(BullyElectionP2p.TAG, "INICIANDO REMOÇÃO DO DISPOSITIVO: " + bullyElectionP2pInstance.registeredClients.toString());
                // Utilização do Iterator para evitar ConcurrentModificationException
                for (Iterator<BullyElectionP2pDevice> it = bullyElectionP2pInstance.registeredClients.iterator(); it.hasNext(); ) {
                    BullyElectionP2pDevice registered = it.next();
                    if (registered.serviceAddress.equals(clientDevice.serviceAddress)) {
                        it.remove();

                        if (bullyElectionP2pInstance.registeredLeader
                                .serviceAddress.equals(clientDevice.serviceAddress)) {
                            bullyElectionP2pInstance.registeredLeader = null;
                        }
                        DeviceInfo reportDeviceRemoval = new DeviceInfo();
                        reportDeviceRemoval.message = DeviceInfo.REMOVE_DEVICE;
                        reportDeviceRemoval.device = registered;
                        bullyElectionP2pInstance.sendToAllDevices(reportDeviceRemoval);

                        EventBus.getDefault().post(new ServerEvent(ServerEvent.DEVICE_UNREGISTERED_WITH_HOST, registered));
                        Log.d(BullyElectionP2p.TAG, "Removido o registro do dispositivo com sucesso");
                    }
                }
                Log.d(BullyElectionP2p.TAG, "REMOÇÃO DO DISPOSITIVO FINALIZADO: " + bullyElectionP2pInstance.registeredClients.toString());
            }

            toClient.close();

        } catch (Exception ex) {
            ex.printStackTrace();
            Log.e(BullyElectionP2p.TAG, "Ocorreu um erro ao lidar com o registro de um cliente");
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                Log.e(BullyElectionP2p.TAG, "Falha ao fechar o socket de registro");
            }
        }
    }
}
