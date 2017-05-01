package br.edu.ffb.pedro.bullyelectionp2p;

import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;
import com.bluelinelabs.logansquare.LoganSquare;

import org.greenrobot.eventbus.EventBus;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

import br.edu.ffb.pedro.bullyelectionp2p.event.DataTransferEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.WifiP2pEvent;
import br.edu.ffb.pedro.bullyelectionp2p.payload.bully.BullyElection;
import okio.BufferedSink;
import okio.Okio;

@SuppressWarnings({"WeakerAccess", "FieldCanBeLocal"})
public class BackgroundDataSendJob implements AsyncJob.OnBackgroundJob {

    private final int BUFFER_SIZE = 65536;
    private Object data;
    private BullyElectionP2pDevice device;

    public BackgroundDataSendJob(BullyElectionP2pDevice device, Object data) {
        this.data = data;
        this.device = device;
    }

    @Override
    public void doOnBackground() {

        if (device.serviceAddress != null) {
            Log.d(BullyElectionP2p.TAG, "\nTentando enviar dados para um dispositivo.");
            Socket dataSocket = new Socket();

            try {
                dataSocket.connect(new InetSocketAddress(device.serviceAddress, device.servicePort), BullyElection.TIMEOUT);
                dataSocket.setReceiveBufferSize(BUFFER_SIZE);
                dataSocket.setSendBufferSize(BUFFER_SIZE);

                // Se esse código for alcançado, um cliente conectou e está transferindo dados
                Log.d(BullyElectionP2p.TAG, "Conectado, transferindo dados...");

                BufferedSink dataStreamToOtherDevice = Okio.buffer(Okio.sink(dataSocket));

                String dataToSend = LoganSquare.serialize(data);
                dataStreamToOtherDevice.writeUtf8(dataToSend);
                dataStreamToOtherDevice.flush();
                dataStreamToOtherDevice.close();

                Log.d(BullyElectionP2p.TAG, "Dados enviados com êxito.");
                EventBus.getDefault().post(new DataTransferEvent(DataTransferEvent.SENT));

            } catch (SocketTimeoutException e) {
                Log.d(BullyElectionP2p.TAG, "A conexxão atingiu o tempo limite: " + device.toString());
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.TIMEOUT, device));
            } catch (Exception ex) {
                Log.d(BullyElectionP2p.TAG, "Ocorreu um erro ao enviar dados para um dispositivo.");
                EventBus.getDefault().post(new DataTransferEvent(DataTransferEvent.FAILURE));
                ex.printStackTrace();
            } finally {
                try {
                    dataSocket.close();
                } catch (Exception ex) {
                    Log.e(BullyElectionP2p.TAG, "Falha ao fechar o socket de dados.");
                }
            }
        } else {
            Log.d(BullyElectionP2p.TAG, "SERVICE ADDRES NULL: " + device.readableName);
        }
    }
}
