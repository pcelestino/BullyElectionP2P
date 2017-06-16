package br.edu.ffb.pedro.bullyelectionp2p;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.net.wifi.WifiManager;
import android.net.wifi.WpsInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceInfo;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.net.wifi.p2p.nsd.WifiP2pServiceInfo;
import android.os.Build;
import android.util.Log;

import com.arasthel.asyncjob.AsyncJob;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import br.edu.ffb.pedro.bullyelectionp2p.callback.OnWifiRestarted;
import br.edu.ffb.pedro.bullyelectionp2p.event.BullyElectionEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.ClientEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.ServerEvent;
import br.edu.ffb.pedro.bullyelectionp2p.event.WifiP2pEvent;
import br.edu.ffb.pedro.bullyelectionp2p.payload.bully.BullyElection;
import br.edu.ffb.pedro.bullyelectionp2p.payload.device.DeviceInfo;

@SuppressWarnings("WeakerAccess")
public class BullyElectionP2p implements WifiP2pManager.ConnectionInfoListener {

    public static final String TAG = "BullyElectionP2p";
    private static final int SERVER_PORT = 37500;
    private static final int SERVICE_PORT = 50489;
    private static final int MAX_SERVER_CONNECTIONS = 80;
    private static final int BUFFER_SIZE = 65536;
    private static final String SERVICE_NAME = "_BullyElectionP2p";
    private static final String SERVICE_TYPE = "_BullyElectionP2p._tcp";
    protected static final String UNREGISTER_CODE = "UNREGISTER_BULLY_ELECTION_P2P_DEVICE";

    protected Context context;

    private ServerSocket serverSocket;
    private ServerSocket listenerServiceSocket;

    public BullyElectionP2pDevice thisDevice;
    public BullyElectionP2pDevice registeredHost;
    public BullyElectionP2pDevice registeredLeader;

    private boolean receiverRegistered;
    protected boolean isConnectedToAnotherDevice;
    private boolean isRunningAsHost;
    private boolean registrationIsRunning;

    //WiFi P2P Objects
    private WifiP2pServiceInfo serviceInfo;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private WifiP2pManager manager;
    private WifiP2pManager.Channel channel;

    private BroadcastReceiver receiver;
    private IntentFilter intentFilter;

    public ArrayList<BullyElectionP2pDevice> registeredClients;
    public ArrayList<BullyElectionP2pDevice> serverDevices;


    @SuppressLint("HardwareIds")
    public BullyElectionP2p(Context context, String readableName) {
        this.context = context;

        EventBus.getDefault().register(BullyElectionP2p.this);

        registeredClients = new ArrayList<>();
        serverDevices = new ArrayList<>();

        thisDevice = new BullyElectionP2pDevice();
        thisDevice.readableName = readableName;
        thisDevice.serviceName = SERVICE_NAME;
        thisDevice.serviceType = SERVICE_TYPE;
        thisDevice.servicePort = SERVICE_PORT;
        thisDevice.serverPort = SERVER_PORT;

        intentFilter = new IntentFilter();
        // Indica uma mudança no status do Wi-Fi P2P.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
        // Indica uma mudança na lista de peers disponíveis.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        // Indica que o status de conectividade da Wi-Fi P2P mudou.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        // Indica ques os detalhes dos dispositivos foram alterados.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);

        manager = (WifiP2pManager) context.getSystemService(Context.WIFI_P2P_SERVICE);
        channel = manager.initialize(context, context.getMainLooper(), new WifiP2pManager.ChannelListener() {
            @Override
            public void onChannelDisconnected() {
                Log.d(TAG, "Reinicializando o canal.");
                channel = manager.initialize(BullyElectionP2p.this.context,
                        BullyElectionP2p.this.context.getMainLooper(), this);
            }
        });

        receiver = new BullyElectionP2pBroadcastReceiver();
    }

    public void startNetworkService() {
        if (!receiverRegistered) {
            Log.d(TAG, "receiver registrado");
            context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }
        manager.clearLocalServices(channel, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                // Configura as informações do serviço
                serviceInfo =
                        WifiP2pDnsSdServiceInfo.newInstance(thisDevice.readableName,
                                SERVICE_TYPE, thisDevice.getTxtRecord());

                // Adiciona um serviço local
                manager.addLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "O serviço local foi adicionado com sucesso");
                        manager.createGroup(channel, new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Grupo criado com sucesso.");
                                Log.d(TAG, SERVICE_NAME + " criado com sucesso e está rodando na porta " + thisDevice.servicePort);
                                isRunningAsHost = true;
                                thisDevice.isHost = true;

                                // A fim de ter um serviço que você criar ser visto, você também deve
                                // ativamente procurar outros serviços. Este é um bug do Android.
                                // Para mais informações, leia aqui.
                                // https://code.google.com/p/android/issues/detail?id=37425
                                // Não precisamos configurar os DNS responders.
                                startServiceDiscovery();
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e(TAG, "Falha ao criar o grupo\n" +
                                        getDiscoverPeersErrorStatus(reason));
                            }
                        });
                    }

                    @Override
                    public void onFailure(int reason) {
                        Log.e(TAG, "Falha ao adicionar o serviço local\n" +
                                getDiscoverPeersErrorStatus(reason));
                    }
                });
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Falha ao remover os serviços locais\n" +
                        getDiscoverPeersErrorStatus(reason));
            }
        });
    }

    public void connectToDevice(final BullyElectionP2pDevice device) {
        WifiP2pConfig config = new WifiP2pConfig();
        config.deviceAddress = device.macAddress;
        config.wps.setup = WpsInfo.PBC;

        manager.connect(channel, config, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                Log.d(TAG, "Conectado com o dispositivo: " + device.deviceName);
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Falha ao se conectar com o dispositivo: " + device.deviceName + "\n" +
                        getDiscoverPeersErrorStatus(reason));
            }
        });
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onWifiP2pEvent(WifiP2pEvent wifiP2pEvent) {
        switch (wifiP2pEvent.event) {
            case WifiP2pEvent.CONNECTED_TO_ANOTHER_DEVICE:
                Log.d(TAG, "Conectado em um dispositivo");
                manager.requestConnectionInfo(channel, this);
                isConnectedToAnotherDevice = true;
                break;
            case WifiP2pEvent.DISCONNECTED_FROM_ANOTHER_DEVICE:
                Log.d(TAG, "Desconectado de outro dispositivo");
                isConnectedToAnotherDevice = false;
                break;
            case WifiP2pEvent.THIS_DEVICE_CHANGED:
                if (thisDevice.deviceName == null) {
                    thisDevice.id = wifiP2pEvent.device.macAddress.hashCode();
                    thisDevice.deviceName = wifiP2pEvent.device.deviceName;
                    thisDevice.macAddress = wifiP2pEvent.device.macAddress;
                    Log.d(TAG, thisDevice.toString());
                }
                break;
            case WifiP2pEvent.WIFI_P2P_DISABLED:
                Log.d(TAG, "WiFi P2P desabilitado");
                break;
            case WifiP2pEvent.WIFI_P2P_ENABLED:
                Log.d(TAG, "WiFi P2P habilitado");
                break;
            case WifiP2pEvent.TIMEOUT:
                final BullyElectionP2pDevice device = wifiP2pEvent.device;
                if (BullyElection.ongoingElection) {

                    // Se chegar nesse ponto significa que iniciou-se uma tentativa
                    // de se conectar ao dispotivo de maior id, porém houve uma falha
                    // de conexão, então o dispositivo aguardará um tempo definido em BuyllyElection
                    // caso haja algum retorno a variável hasElectionResponse e não será efetuado nenhum
                    // procedimento, caso contrário, esse dispositivo irá se declarar líder
                    AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
                        @Override
                        public void doOnBackground() {
                            try {
                                Thread.sleep(BullyElection.TIMEOUT);

                                // Se a variável não for alterada, significa que não houve resposta
                                if (!BullyElection.hasElectionResponse) {
                                    Log.d(BullyElectionP2p.TAG, "Não houve resposta do dispositivo " +
                                            "de maior id, o dispositivo atual irá se declarar líder");
                                    informLeader();
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                } else {

                    // TODO: Verificar a necessidade de um timeout adicional
                    Log.d(TAG, "REMOVENDO A REFERÊNCIA DO DISPOSITIVO: " + device.readableName);
                    removeDeviceReference(device);
                    informDeviceReferenceRemoved(device);

                    // Timeout para que haja tempo das referências serem removidas
                    AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
                        @Override
                        public void doOnBackground() {
                            try {
                                Thread.sleep(BullyElection.TIMEOUT / 2);
                                // Se o dispositivo era o líder, será iniciada uma nova eleição
                                if (device.isLeader) {
                                    Log.d(BullyElectionP2p.TAG, "Líder desconectado, iniciando uma nova eleição");
                                    registeredLeader = null;
                                    startElection();
                                }
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void onConnectionInfoAvailable(final WifiP2pInfo info) {
        /* Este método é automaticamente chamado quando nos conectamos a um dispositivo.
           O proprietário do grupo aceita conexões usando um server socket e, em seguida,
           gera um client socket para cada cliente. Isso é tratado pelos registration jobs.
           Isto irá lidar automaticamente com as primeiras conexões. */

        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(WifiP2pGroup group) {
                if (isRunningAsHost && !registrationIsRunning) {
                    if (info.groupFormed && !group.getClientList().isEmpty()) {
                        startHostRegistrationServer();
                    }
                } else if (!thisDevice.isRegistered && !info.isGroupOwner) {
                    if (serviceRequest == null) {
                        //Isso significa que discoverNetworkServices nunca foi chamado e ainda estamos conectados a um host antigo por algum motivo.
                        Log.e(TAG, "Este dispositivo ainda está conectado a um host antigo por algum motivo. Uma desconexão forçada será tentada.");
                        forceDisconnect();
                    }
                    Log.d(TAG, "Conectado com êxito a outro dispositivo.");
                    startRegistrationForClient(new InetSocketAddress(info.groupOwnerAddress.getHostAddress(), SERVER_PORT));
                }
            }
        });
    }

    private void obtainServerPortLock() {
        if (serverSocket == null || serverSocket.isClosed()) {
            try {
                serverSocket = new ServerSocket(SERVER_PORT, MAX_SERVER_CONNECTIONS);
                serverSocket.setReuseAddress(true);
                serverSocket.setReceiveBufferSize(BUFFER_SIZE);
            } catch (IOException ex) {
                Log.e(TAG, "Server: Falha ao usar a porta padrão, outra será usada em vez disso");

                try {
                    serverSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    serverSocket.setReuseAddress(true);
                    serverSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.serverPort = serverSocket.getLocalPort();
                } catch (IOException ioEx) {
                    Log.e(TAG, "Falha ao obter uma porta aleatória, "
                            + thisDevice.serviceName + " não funcionará corretamente");
                }

            }
        }
    }

    private void obtainClientServicePortLock() {
        if (listenerServiceSocket == null || listenerServiceSocket.isClosed()) {
            try {
                listenerServiceSocket = new ServerSocket(thisDevice.servicePort, MAX_SERVER_CONNECTIONS);
                listenerServiceSocket.setReuseAddress(true);
                listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
            } catch (IOException ex) {
                Log.e(TAG, "Listener Service: Falha ao usar porta padrão, outra será usada em vez disso.");

                try {
                    listenerServiceSocket = new ServerSocket(0, MAX_SERVER_CONNECTIONS);
                    listenerServiceSocket.setReuseAddress(true);
                    listenerServiceSocket.setReceiveBufferSize(BUFFER_SIZE);
                    thisDevice.servicePort = listenerServiceSocket.getLocalPort();
                } catch (IOException ioEx) {
                    Log.e(TAG, "Falha ao obter uma porta aleatória, "
                            + thisDevice.serviceName + " não funcionará corretamente.");
                }

            }
        }
    }

    public void discoverNetworkServices() {
        prepareServiceDiscovery();
        startServiceDiscovery();
    }

    private void prepareServiceDiscovery() {
         /* Aqui, registramos um ouvinte para quando os serviços são realmente encontrados. A
            especificação WiFi P2P observa que precisamos de dois tipos de ouvintes, um para um
            serviço DNS e outro para um registro TXT. O ouvinte do serviço DNS é invocado sempre que
            um serviço é encontrado, independentemente de ser ou não seu. Para isso determinar se é,
            temos de comparar o nosso nome de serviço com o nome do serviço. Se for o nosso serviço,
            simplesmente registramos. */

        WifiP2pManager.DnsSdServiceResponseListener serviceListener = new WifiP2pManager.DnsSdServiceResponseListener() {
            @Override
            public void onDnsSdServiceAvailable(String instanceName, String serviceNameAndTP, WifiP2pDevice sourceDevice) {

                Log.d(TAG, "Dispositivo encontrado: "
                        + instanceName + " "
                        + sourceDevice.deviceName + " "
                        + serviceNameAndTP);
            }
        };

        /* O registro TXT contém informações específicas sobre um serviço e seu ouvinte também pode
           ser invocado independentemente do dispositivo. Aqui, nós verificamos se o dispositivo é
           nosso, e então nós seguimos em frente e puxamos aquela informação específica dele e a
           colocamos em um Mapa. A função que foi passada no início também é chamada */
        WifiP2pManager.DnsSdTxtRecordListener txtRecordListener = new WifiP2pManager.DnsSdTxtRecordListener() {
            @Override
            public void onDnsSdTxtRecordAvailable(String serviceFullDomainName, Map<String, String> record, WifiP2pDevice device) {
                if (!serverDevices.isEmpty()) {
                    for (BullyElectionP2pDevice serverDevice : serverDevices) {
                        if (serverDevice.deviceName.equals(device.deviceName)) {
                            return;
                        }
                    }
                }
                if (record.containsValue(thisDevice.serviceName)) {
                    BullyElectionP2pDevice serverDevice = new BullyElectionP2pDevice(device, record);
                    serverDevices.add(serverDevice);
                    EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.SERVER_DEVICE_FOUND, serverDevice));
                }
            }
        };

        manager.setDnsSdResponseListeners(channel, serviceListener, txtRecordListener);
    }

    private void startServiceDiscovery() {
        serverDevices.clear();

        if (!receiverRegistered) {
            Log.d(TAG, "receiver registrado");
            context.registerReceiver(receiver, intentFilter);
            receiverRegistered = true;
        }

        // Após anexar ouvintes, crie um pedido de serviço e inicie
        // descoberta.
        serviceRequest = WifiP2pDnsSdServiceRequest.newInstance();

        manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
            @Override
            public void onSuccess() {
                manager.addServiceRequest(channel, serviceRequest,
                        new WifiP2pManager.ActionListener() {
                            @Override
                            public void onSuccess() {
                                Log.d(TAG, "Solicitação de descoberta de serviço reconhecida");

                                manager.discoverServices(channel, new WifiP2pManager.ActionListener() {
                                    @Override
                                    public void onSuccess() {
                                        Log.d(TAG, "Início da descoberta do serviço.");
                                    }

                                    @Override
                                    public void onFailure(int reason) {
                                        Log.e(TAG, "A descoberta de serviços falhou\n" +
                                                getDiscoverPeersErrorStatus(reason));

                                        if (reason == WifiP2pManager.NO_SERVICE_REQUESTS) {
                                            disableWiFi(context);
                                            enableWiFi(context);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onFailure(int reason) {
                                Log.e(TAG, "Falha ao adicionar solicitação de descoberta de serviços\n" +
                                        getDiscoverPeersErrorStatus(reason));
                            }
                        });
            }

            @Override
            public void onFailure(int reason) {
                Log.e(TAG, "Falha ao remover a descoberta de serviços\n" +
                        getDiscoverPeersErrorStatus(reason));
            }
        });
    }

    private void startRegistrationForClient(final InetSocketAddress hostDeviceAddress) {

        BackgroundClientRegistrationJob registrationJob = new BackgroundClientRegistrationJob(this, hostDeviceAddress);
        AsyncJob.doInBackground(registrationJob);
    }

    private void startHostRegistrationServer() {
        obtainServerPortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {

                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.
                    registrationIsRunning = true;
                    while (isRunningAsHost) {
                        Log.d(TAG, "\nOuvindo dados de registro...");
                        Socket clientSocket = serverSocket.accept();
                        BackgroundServerRegistrationJob registrationJob = new BackgroundServerRegistrationJob(BullyElectionP2p.this, clientSocket);

                        AsyncJob.doInBackground(registrationJob);
                    }
                    registrationIsRunning = false;
                } catch (Exception ex) {
                    Log.e(TAG, "Ocorreu um erro na thread de registro do servidor.");
                    ex.printStackTrace();
                }
            }
        });
    }

    protected void startListeningForData() {
        obtainClientServicePortLock();

        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                try {
                    //Create a server socket and wait for client connections. This
                    //call blocks until a connection is accepted from a client.

                    while (isRunningAsHost || thisDevice.isRegistered) {

                        Log.d(TAG, "\nOuvindo dados de serviço...");

                        Socket dataListener = listenerServiceSocket.accept();
                        BackgroundDataJob dealWithData = new BackgroundDataJob(dataListener, BullyElectionP2p.this);

                        AsyncJob.doInBackground(dealWithData);
                    }

                } catch (Exception ex) {
                    Log.e(TAG, "Ocorreu um erro na thread de escuta de dados do servidor.");
                    ex.printStackTrace();
                }
            }
        });
    }

    public void sendToAllDevices(Object data) {
        for (BullyElectionP2pDevice registered : registeredClients) {
            if (registered.id != thisDevice.id)
                sendData(registered, data);
        }
    }

    public void sendToHost(Object data) {
        if (!isRunningAsHost && thisDevice.isRegistered) {
            if (thisDevice.isLeader) {
                sendData(registeredHost, data);
            } else {
                sendToLeader(data);
            }
        } else {
            Log.e(TAG, "Este dispositivo é o host e, portanto, não pode invocar este método.");
        }
    }

    public void sendToDevice(BullyElectionP2pDevice device, Object data) {
        sendData(device, data);
    }

    private void sendData(BullyElectionP2pDevice device, Object data) {
        BackgroundDataSendJob sendDataToDevice = new BackgroundDataSendJob(device, this, data);
        AsyncJob.doInBackground(sendDataToDevice);
    }

    public void unregisterEventBus() {
        EventBus.getDefault().unregister(BullyElectionP2p.this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopNetworkService(final boolean disableWiFi) {
        if (isRunningAsHost) {
            unregisterEventBus();
            Log.d(TAG, "Parando o serviço de rede...");
            stopServiceDiscovery(true);
            closeDataSocket();
            closeRegistrationSocket();

            if (manager != null && channel != null && serviceInfo != null) {

                manager.removeLocalService(channel, serviceInfo, new WifiP2pManager.ActionListener() {
                    @Override
                    public void onFailure(int reason) {
                        Log.d(TAG, "Não foi possível terminar o serviço. Razão : " + reason);
                        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CLOSED, thisDevice));
                    }

                    @Override
                    public void onSuccess() {
                        Log.d(TAG, "Serviço encerrado com sucesso.");
                        if (disableWiFi) {
                            disableWiFi(context); //Called here to give time for request to be disposed.
                        }
                        isRunningAsHost = false;
                        thisDevice.isHost = false;
                        EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CLOSED, thisDevice));
                    }
                });

            } else {
                EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CLOSED, thisDevice));
            }

        } else {
            Log.d(TAG, "O serviço de rede não está em execução.");
            EventBus.getDefault().post(new ServerEvent(ServerEvent.SERVER_CLOSED, thisDevice));
        }
    }

    public void unregisterClient(boolean disableWiFi) {

        unregisterEventBus();
        BackgroundClientRegistrationJob.disableWiFiOnUnregister = disableWiFi;

        if (receiverRegistered) {
            Log.d(TAG, "Removendo receiver");
            context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (!isConnectedToAnotherDevice) {
            Log.d(TAG, "Tentativa de cancelar o registro, mas não conectado ao grupo. O serviço remoto pode já estar desligado.");
            thisDevice.isRegistered = false;
            registeredHost = null;
            closeDataSocket();
            disconnectFromDevice();
            EventBus.getDefault().post(new ClientEvent(ClientEvent.UNREGISTERED));
        } else {
            startRegistrationForClient(new InetSocketAddress(registeredHost.serviceAddress, SERVER_PORT));
        }
    }

    protected void forceDisconnect() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            WifiP2pManager.ActionListener doNothing = new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                }

                @Override
                public void onFailure(int reason) {
                }
            };

            stopServiceDiscovery(false);
            manager.cancelConnect(channel, doNothing);
            manager.clearLocalServices(channel, doNothing);
            manager.clearServiceRequests(channel, doNothing);
            manager.stopPeerDiscovery(channel, doNothing);
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    public void stopServiceDiscovery(boolean shouldUnregister) {

        if (isConnectedToAnotherDevice)
            disconnectFromDevice();

        if (shouldUnregister) {
            Log.d(TAG, "Removido o registro de BullyElectionP2p receiver.");
            context.unregisterReceiver(receiver);
            receiverRegistered = false;
        }

        if (manager != null && channel != null) {
            manager.removeServiceRequest(channel, serviceRequest, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Solicitação de descoberta de serviço removido com êxito.");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Falha ao remover solicitação de descoberta de serviço\n" +
                            getDiscoverPeersErrorStatus(reason));
                }
            });
        }
    }

    protected void disconnectFromDevice() {
        manager.requestGroupInfo(channel, new WifiP2pManager.GroupInfoListener() {
            @Override
            public void onGroupInfoAvailable(final WifiP2pGroup group) {
                if (group != null) {
                    manager.removeGroup(channel, new WifiP2pManager.ActionListener() {
                        @Override
                        public void onSuccess() {
                            isConnectedToAnotherDevice = false;
                            deletePersistentGroup(group);
                            Log.d(TAG, "WiFi Direct Group Removido");
                        }

                        @Override
                        public void onFailure(int reason) {
                            Log.e(TAG, "Falha ao remover o WiFi Direct Group\n" +
                                    getDiscoverPeersErrorStatus(reason));
                        }
                    });
                }
            }
        });
    }

    private void deletePersistentGroup(WifiP2pGroup wifiP2pGroup) {
        try {

            Method getNetworkId = WifiP2pGroup.class.getMethod("getNetworkId");
            Integer networkId = (Integer) getNetworkId.invoke(wifiP2pGroup);
            Method deletePersistentGroup = WifiP2pManager.class.getMethod("deletePersistentGroup",
                    WifiP2pManager.Channel.class, int.class, WifiP2pManager.ActionListener.class);
            deletePersistentGroup.invoke(manager, channel, networkId, new WifiP2pManager.ActionListener() {
                @Override
                public void onSuccess() {
                    Log.d(TAG, "Persistent Group Removido");
                }

                @Override
                public void onFailure(int reason) {
                    Log.e(TAG, "Falha ao remover o Persistent Group\n" +
                            getDiscoverPeersErrorStatus(reason));
                }
            });
        } catch (Exception ex) {
            Log.e(TAG, "Não foi possível deletar o persistent group", ex);
        }
    }

    protected void closeRegistrationSocket() {
        try {
            if (registrationIsRunning) {
                serverSocket.close();
                Log.d(TAG, "Registration sockets fechados.");
            }
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao fechar o registration socket.");
        }

        registrationIsRunning = false;
    }

    protected void closeDataSocket() {
        try {
            listenerServiceSocket.close();
            Log.d(TAG, "Parou de ouvir os dados do serviço.");
        } catch (Exception ex) {
            Log.e(TAG, "Falha ao fechar o listening socket");
        }
    }

    public static void restartWiFi(final Context context, final OnWifiRestarted onWifiRestarted) {
        disableWiFi(context);
        AsyncJob.doInBackground(new AsyncJob.OnBackgroundJob() {
            @Override
            public void doOnBackground() {
                try {
                    Thread.sleep(2000);
                    AsyncJob.doOnMainThread(new AsyncJob.OnMainThreadJob() {
                        @Override
                        public void doInUIThread() {
                            enableWiFi(context);
                            onWifiRestarted.call();
                        }
                    });
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public static void enableWiFi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(true);
    }

    public static void disableWiFi(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wifiManager.setWifiEnabled(false);
    }

    private String getDiscoverPeersErrorStatus(int deviceStatus) {
        switch (deviceStatus) {
            case WifiP2pManager.P2P_UNSUPPORTED:
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.P2P_UNSUPPORTED));
                return "Dispositivo não suporta P2P";
            case WifiP2pManager.BUSY:
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.ERROR));
                return "Framework está ocupado ou está incapaz de atender ao pedido";
            case WifiP2pManager.ERROR:
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.ERROR));
                return "Erro interno";
            case WifiP2pManager.NO_SERVICE_REQUESTS:
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.ERROR));
                return "Sem serviços disponíveis";
            default:
                EventBus.getDefault().post(new WifiP2pEvent(WifiP2pEvent.ERROR));
                return "Desconhecido";
        }
    }

    // BULLY ELECTION
    public void bootstrapElection() {
        BullyElection bullyElection = new BullyElection(BullyElection.START_ELECTION, thisDevice);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        sendToAllDevicesSync(executorService, registeredClients, bullyElection);
    }

    public void startElection() {

        Log.d(TAG, "Iniciando uma nova eleição");
        boolean hasDeviceWithHigherId = false;

        BullyElection bullyElection = new BullyElection(BullyElection.START_ELECTION, thisDevice);

        // Executa as threads de forma síncrona
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        for (BullyElectionP2pDevice device : registeredClients) {
            if (device.id > thisDevice.id) {
                hasDeviceWithHigherId = true;
                sendDataSync(executorService, device, bullyElection);
            }
        }

        if (!hasDeviceWithHigherId) {
            BullyElection.hasElectionResponse = false;
            BullyElection.ongoingElection = false;
            thisDevice.isLeader = true;
            updateLeaderReference(thisDevice);
            informLeader();
            Log.d(TAG, "Informando líder: " + thisDevice.toString());
        }
    }

    public void informLeader() {
        // O EventBus informa ao dispositivo atual, para caso tenha sido registrado esse evento
        EventBus.getDefault().post(new BullyElectionEvent(BullyElectionEvent.ELECTED_LEADER, thisDevice));

        // Envia para todos os clientes registrados rede local menos o dispositivo atual
        BullyElection bullyElection = new BullyElection(BullyElection.INFORM_LEADER, thisDevice);
        sendToAllDevices(bullyElection);

        // Envia para o Host
        sendToHost(bullyElection);
    }

    public void updateLeaderReference(BullyElectionP2pDevice leader) {
        if (registeredLeader != null) {
            for (BullyElectionP2pDevice registeredClient : registeredClients) {
                if (registeredClient.isLeader) {
                    registeredClient.isLeader = false;
                    registeredLeader = null;
                    break;
                }
            }
        }
        for (BullyElectionP2pDevice registeredClient : registeredClients) {
            if (registeredClient.id == leader.id) {
                registeredClient.isLeader = true;
                if (!thisDevice.isLeader) registeredLeader = registeredClient;
                break;
            }
        }
    }

    public void removeDeviceReference(BullyElectionP2pDevice device) {
        // Utilização do Iterator para evitar ConcurrentModificationException
        for (Iterator<BullyElectionP2pDevice> it = registeredClients.iterator(); it.hasNext(); ) {
            BullyElectionP2pDevice registeredClient = it.next();
            if (registeredClient.id == device.id) {
                it.remove();
                Log.d(BullyElectionP2p.TAG, "Removido o registro do dispositivo com sucesso");
            }
        }
    }

    public void informDeviceReferenceRemoved(BullyElectionP2pDevice device) {
        // Remove a referencia do device em todos os clientes e no host
        DeviceInfo deviceInfo = new DeviceInfo();
        deviceInfo.message = DeviceInfo.REMOVE_DEVICE;
        deviceInfo.device = device;
        sendToAllDevices(deviceInfo);
        sendToHost(deviceInfo);
    }

    private void sendToAllDevicesSync(ExecutorService executorService, ArrayList<BullyElectionP2pDevice> devices, Object data) {
        for (BullyElectionP2pDevice device : devices) {
            sendDataSync(executorService, device, data);
        }
    }

    private void sendDataSync(ExecutorService executorService, BullyElectionP2pDevice device, Object data) {
        BackgroundDataSendJob sendDataToDevice = new BackgroundDataSendJob(device, this, data);
        AsyncJob.doInBackground(sendDataToDevice, executorService);
    }

    public void sendToLeader(Object data) {
        if (registeredLeader != null) {
            sendData(registeredLeader, data);
        } else {
            Log.e(TAG, "Nenhum líder eleito, iniciando uma nova eleição");
            startElection();
        }
    }
}
