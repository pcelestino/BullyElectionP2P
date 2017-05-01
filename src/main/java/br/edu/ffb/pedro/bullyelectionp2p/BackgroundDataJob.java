package br.edu.ffb.pedro.bullyelectionp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import org.greenrobot.eventbus.EventBus;

import java.io.IOException;
import java.net.Socket;

import br.edu.ffb.pedro.bullyelectionp2p.event.BullyElectionEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.DataTransferEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.ServerEvent;
import br.edu.ffb.pedro.bullyelectionp2p.payload.Payload;
import br.edu.ffb.pedro.bullyelectionp2p.payload.bully.BullyElection;
import br.edu.ffb.pedro.bullyelectionp2p.payload.device.DeviceInfo;
import okio.BufferedSource;
import okio.Okio;


@SuppressWarnings("WeakerAccess")
public class BackgroundDataJob implements AsyncJob.OnBackgroundJob {

    private Socket clientSocket;
    BullyElectionP2p bullyElectionP2pInstance;

    public BackgroundDataJob(Socket clientSocket, BullyElectionP2p bullyElectionP2pInstance) {
        this.clientSocket = clientSocket;
        this.bullyElectionP2pInstance = bullyElectionP2pInstance;
    }

    @Override
    public void doOnBackground() {
        try {

            //Se esse código for atingido, um cliente conectou e transferiu dados.
            Log.d(BullyElectionP2p.TAG, "Um dispositivo está enviando dados...");
            BufferedSource dataStreamFromOtherDevice = Okio.buffer(Okio.source(clientSocket));
            String data = dataStreamFromOtherDevice.readUtf8();

            if (!data.isEmpty()) {
                Payload payload = LoganSquare.parse(data, Payload.class);
                Log.d(BullyElectionP2p.TAG, "PAYLOAD TYPE: " + payload.type);
                Log.d(BullyElectionP2p.TAG, "PAYLOAD DATA: " + data);
                Log.d(BullyElectionP2p.TAG, "DEVICE LIST: " + bullyElectionP2pInstance.registeredClients.toString());
                switch (payload.type) {
                    case DeviceInfo.TYPE:
                        Log.d(BullyElectionP2p.TAG, "DeviceInfo payload");
                        DeviceInfo deviceInfo = LoganSquare.parse(data, DeviceInfo.class);
                        switch (deviceInfo.message) {
                            case DeviceInfo.INFORM_DEVICE:
                                Log.d(BullyElectionP2p.TAG, "DeviceInfo adicionando dispositivo: " + deviceInfo.device.toString());
                                bullyElectionP2pInstance.registeredClients.add(deviceInfo.device);
                                break;
                            case DeviceInfo.REMOVE_DEVICE:
                                Log.d(BullyElectionP2p.TAG, "DeviceInfo removendo dispositivo: " + deviceInfo.device.toString());
                                bullyElectionP2pInstance.removeDeviceReference(deviceInfo.device);
                                EventBus.getDefault().post(new ServerEvent(ServerEvent.DEVICE_UNREGISTERED_WITH_HOST, deviceInfo.device));
                        }
                        break;
                    case BullyElection.TYPE:
                        BullyElection bullyElection = LoganSquare.parse(data, BullyElection.class);
                        switch (bullyElection.message) {
                            case BullyElection.START_ELECTION:
                                if (!BullyElection.hasElectionResponse) {
                                    BullyElection.ongoingElection = true;
                                    if (!bullyElection.device.isHost) {
                                        BullyElectionP2pDevice responseDevice = bullyElection.device;
                                        bullyElectionP2pInstance.sendToDevice(responseDevice,
                                                new BullyElection(BullyElection.RESPOND_OK, responseDevice));
                                    }
                                    bullyElectionP2pInstance.startElection();
                                }
                                break;
                            case BullyElection.RESPOND_OK:
                                BullyElection.hasElectionResponse = true;
                                break;
                            case BullyElection.INFORM_LEADER:
                                bullyElectionP2pInstance.updateLeaderReference(bullyElection.device);
                                BullyElection.hasElectionResponse = false;
                                BullyElection.ongoingElection = false;
                                EventBus.getDefault().post(new BullyElectionEvent(
                                        BullyElectionEvent.ELECTED_LEADER, bullyElection.device)
                                );
                                break;
                        }
                        break;
                    default:
                        EventBus.getDefault().post(new DataTransferEvent(DataTransferEvent.DATA_RECEIVED, data));
                }
            }
        } catch (IOException e) {
            Log.d(BullyElectionP2p.TAG, "Falha ao efetuar um parse de dados com o LoganSquare");
        } catch (Exception ex) {
            Log.e(BullyElectionP2p.TAG, "Ocorreu um erro ao tentar receber os dados.");
            ex.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (Exception ex) {
                Log.e(BullyElectionP2p.TAG, "Falha ao fechar o socket de dados.");
            }
        }
    }
}
