package org.opendaylight.controller.packetcable.provider;

import java.io.IOException;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.binding.api.ReadOnlyTransaction;
import org.opendaylight.controller.md.sal.binding.api.WriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataBroker.DataChangeScope;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.controller.md.sal.common.api.data.ReadFailedException;
import org.opendaylight.controller.packetcable.provider.processors.PCMMDataProcessor;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Ccaps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.CcapsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Qos;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.classifier.Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.AppsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.Subs;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.SubsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.Gates;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.GatesKey;
import org.opendaylight.yangtools.concepts.CompositeObjectRegistration;
import org.opendaylight.yangtools.concepts.CompositeObjectRegistration.CompositeObjectRegistrationBuilder;
import org.opendaylight.yangtools.concepts.ListenerRegistration;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier.PathArgument;
import org.opendaylight.yangtools.yang.binding.KeyedInstanceIdentifier;
import org.opendaylight.yangtools.yang.binding.RpcService;
import org.opendaylight.yangtools.yang.common.RpcResult;
import org.opendaylight.yangtools.yang.common.RpcResultBuilder;
import org.pcmm.PCMMDef;
import org.pcmm.PCMMPdpAgent;
import org.pcmm.PCMMPdpDataProcess;
import org.pcmm.PCMMPdpMsgSender;
import org.pcmm.gates.IClassifier;
import org.pcmm.gates.IGateID;
import org.pcmm.gates.IGateSpec;
import org.pcmm.gates.IPCMMGate;
import org.pcmm.gates.ITrafficProfile;
import org.pcmm.gates.impl.GateID;
import org.pcmm.gates.impl.PCMMGateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umu.cops.prpdp.COPSPdpException;
import org.umu.cops.stack.COPSError;
import org.umu.cops.stack.COPSException;

import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;

import org.pcmm.rcd.IPCMMPolicyServer;
import org.pcmm.rcd.IPCMMPolicyServer.IPSCMTSClient;
import org.pcmm.rcd.impl.PCMMPolicyServer;



@SuppressWarnings("unused")
public class PacketcableProvider implements DataChangeListener, AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(PacketcableProvider.class);

	private final ExecutorService executor;

	// The following holds the Future for the current make toast task.
	// This is used to cancel the current toast.
	private final AtomicReference<Future<?>> currentConnectionsTasks = new AtomicReference<>();
	private DataBroker dataBroker;
	private ListenerRegistration<DataChangeListener> listenerRegistration;
	private List<InstanceIdentifier<?>> cmtsInstances = Lists.newArrayList();

	private Map<String, Ccaps> ccapMap = Maps.newConcurrentMap();
	private Map<String, Gates> gateMap = new ConcurrentHashMap<String, Gates>();
	private PCMMDataProcessor gateBuilder;
	private PcmmService pcmmService;

	public static final InstanceIdentifier<Ccaps> ccapsIID = InstanceIdentifier.builder(Ccaps.class).build();
	public static final InstanceIdentifier<Qos> qosIID = InstanceIdentifier.builder(Qos.class).build();

	public PacketcableProvider() {
		executor = Executors.newCachedThreadPool();
		gateBuilder = new PCMMDataProcessor();
		pcmmService = new PcmmService();
	}

//	public void setNotificationProvider(final NotificationProviderService salService) {
//		this.notificationProvider = salService;
//	}

	public void setDataBroker(final DataBroker salDataProvider) {
		this.dataBroker = salDataProvider;
	}

	/**
	 * Implemented from the AutoCloseable interface.
	 */
	@Override
	public void close() throws ExecutionException, InterruptedException {
		executor.shutdown();
		if (dataBroker != null) {
			for (Iterator<InstanceIdentifier<?>> iter = cmtsInstances.iterator(); iter.hasNext();) {
				WriteTransaction tx = dataBroker.newWriteOnlyTransaction();
				tx.delete(LogicalDatastoreType.OPERATIONAL, iter.next());
				Futures.addCallback(tx.submit(), new FutureCallback<Void>() {
					@Override
					public void onSuccess(final Void result) {
						logger.debug("Delete commit result: " + result);
					}

					@Override
					public void onFailure(final Throwable t) {
						logger.error("Delete operation failed", t);
					}
				});
			}
		}
	}

	/*
	 * PCMM services -- locally implemented from packetcable-consumer.PcmmServiceImpl.java
	 * sync call only, no notification required
	 */
	private class CcapClient {
		protected PCMMPdpDataProcess pcmmProcess;
	    protected PCMMPdpAgent pcmmPdp;
	    protected PCMMPdpMsgSender pcmmSender;
	    String ipv4 = null;
	    Integer port = PCMMPdpAgent.WELL_KNOWN_PDP_PORT;
	    Boolean isConnected = false;

    	public CcapClient() {
			pcmmPdp = new PCMMPdpAgent(PCMMDef.C_PCMM, pcmmProcess);
    	}

        public void connect(IpAddress ccapIp, PortNumber portNum ) {
        	ipv4 = ccapIp.getIpv4Address().getValue();
        	if (portNum != null) {
        		port = portNum.getValue();
        	}
            logger.info("CcapClient: connect(): {}:{}", ipv4, port);
            try  {
                pcmmPdp.connect(ipv4, port);
                isConnected = true;
            } catch (Exception e) {
                logger.error("CcapClient: connect(): {}:{} FAILED: {}", ipv4, port, e.getMessage());
            }
        }

        public void disconnect() {
            logger.info("CcapClient: disconnect(): {}:{}", ipv4, port);
        	try {
				pcmmPdp.disconnect(pcmmPdp.getPepIdString(), null);
				isConnected = false;
			} catch (COPSException | IOException e) {
                logger.error("CcapClient: disconnect(): {}:{} FAILED: {}", ipv4, port, e.getMessage());
			}
        }

        public Boolean sendGateSet(PCMMGateReq gateReq) {
            logger.info("CcapClient: sendGateSet(): {}:{} => {}", ipv4, port, gateReq);
        	try {
                pcmmSender = new PCMMPdpMsgSender(PCMMDef.C_PCMM, pcmmPdp.getClientHandle(), pcmmPdp.getSocket());
				pcmmSender.sendGateSet(gateReq);
			} catch (COPSPdpException e) {
                logger.error("CcapClient: sendGateSet(): {}:{} => {} FAILED: {}", ipv4, port, gateReq, e.getMessage());
			}
        	while (pcmmSender.getGateID() == null) {
        		// wait for pcmmSender.handleGateReport(socket) to come back with gateID
        		try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					break;
				}
        	}
        	// and save it back to the gateRequest object for gate delete later
    		gateReq.setGateID(pcmmSender.getGateID());
			return true;
        }

        public Boolean sendGateDelete(IGateID gateId) {
        	try {
                pcmmSender = new PCMMPdpMsgSender(PCMMDef.C_PCMM, pcmmPdp.getClientHandle(), pcmmPdp.getSocket());
				pcmmSender.sendGateDelete(gateId.getGateID());
			} catch (COPSPdpException e) {
                logger.error("CcapClient: sendGateDelete(): {}:{} => {} FAILED: {}", ipv4, port, gateId, e.getMessage());
			}

			return true;

        }

	}

	private class PcmmService {
		private Map<IpAddress, CcapClient> ccapClients = Maps.newConcurrentMap();
		private Map<String, PCMMGateReq> gateRequests = Maps.newConcurrentMap();

		public PcmmService() {
		}

		public void addCcap(Ccaps ccap) {
			IpAddress ipAddr = ccap.getConnection().getIpAddress();
			PortNumber port = ccap.getConnection().getPort();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("addCcap(): " + ipv4);
			CcapClient client = new CcapClient();
			client.connect(ipAddr, port);
			if (client.isConnected) {
				ccapClients.put(ipAddr, client);
				logger.info("addCcap(): connected:" + ipv4);
			}
		}

		public void removeCcap(Ccaps ccap) {
			IpAddress ipAddr = ccap.getConnection().getIpAddress();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("removeCcap(): " + ipv4);
			if (ccapClients.containsKey(ipAddr)) {
				CcapClient client = ccapClients.remove(ipAddr);
				client.disconnect();
				logger.info("removeCcap(): disconnected: " + ipv4);
			}
		}

		public Boolean sendGateSet(String gatePathStr, Gates qosGate) {
			//TODO: find the matching CCAP for this subId
			CcapClient ccap = ccapClients.values().iterator().next();
			// build the gate request
			PCMMGateReq gateReq = new PCMMGateReq();
			IGateSpec gateSpec = gateBuilder.build(qosGate.getGateSpec());
			gateReq.setGateSpec(gateSpec);
			ITrafficProfile trafficProfile = gateBuilder.build(qosGate.getTrafficProfile());
			gateReq.setTrafficProfile(trafficProfile);
			IClassifier classifier = gateBuilder.build(qosGate.getClassifier());
			gateReq.setClassifier(classifier);
			// send it to the CCAP
			boolean ret = ccap.sendGateSet(gateReq);
			// and remember it
			gateRequests.put(gatePathStr, gateReq);
			return ret;
		}

		public Boolean sendGateDelete(String gatePathStr) {
			//TODO: find the matching CCAP for this subId
			CcapClient ccap = ccapClients.values().iterator().next();
			PCMMGateReq gateReq = gateRequests.remove(gatePathStr);
			Boolean ret = ccap.sendGateDelete(gateReq.getGateID());
			return ret;
		}
/*
		public Boolean sendGateSynchronize() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = ccapClients.values().iterator(); iter.hasNext();)
				ret &= ccapClients.get(0).gateSynchronize();
			return ret;
		}

		public Boolean sendGateInfo() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = ccapClients.values().iterator(); iter.hasNext();)
				ret &= ccapClients.get(0).gateInfo();
			return ret;
		}
*/
	}


	/**
	 * Implemented from the DataChangeListener interface.
	 */

	private class InstanceData {
		public Map<String, String> gatePathMap = new HashMap<String, String>();
		public String gatePath;
		public String ccapId;
		public Ccaps ccap;
		public List<Gates> gateList = new ArrayList<Gates>();
		public Set<String> removePathList = new HashSet<String>();

		public InstanceData(Map<InstanceIdentifier<?>, DataObject> thisData) {
			// only used to parse createdData or updatedData
			getCcap(thisData);
			if (ccap != null) {
				ccapId = ccap.getCcapId();
				gatePath = ccapId;
			} else {
				getGates(thisData);
				if (! gateList.isEmpty()){
					gatePath = gatePathMap.get("appId") + "/" + gatePathMap.get("subId");
				}
			}
		}

		public InstanceData(Set<InstanceIdentifier<?>> thisData) {
			// only used to parse the removedData paths
			for (InstanceIdentifier<?> removeThis : thisData) {
				getGatePathMap(removeThis);
				if (gatePathMap.containsKey("ccapId")) {
					gatePath = gatePathMap.get("ccapId");
					removePathList.add(gatePath);
				} else if (gatePathMap.containsKey("gateId")) {
					gatePath = gatePathMap.get("appId") + "/" + gatePathMap.get("subId") + "/" + gatePathMap.get("gateId");
					removePathList.add(gatePath);
				}
			}
		}
		private void getGatePathMap(InstanceIdentifier<?> thisInstance) {
			logger.debug("onDataChanged().getGatePathMap(): " + thisInstance);
			try {
				InstanceIdentifier<Ccaps> ccapInstance = thisInstance.firstIdentifierOf(Ccaps.class);
				if (ccapInstance != null) {
					CcapsKey ccapKey = InstanceIdentifier.keyOf(ccapInstance);
					if (ccapKey != null) {
						String ccapId = ccapKey.getCcapId();
						gatePathMap.put("ccapId", ccapId);
					}
				} else {
					// get the gate path keys from the InstanceIdentifier Map key set if they are there
					InstanceIdentifier<Apps> appsInstance = thisInstance.firstIdentifierOf(Apps.class);
					if (appsInstance != null) {
						AppsKey appKey = InstanceIdentifier.keyOf(appsInstance);
						if (appKey != null) {
							String appId = appKey.getAppId();
							gatePathMap.put("appId", appId);
						}
					}
					InstanceIdentifier<Subs> subsInstance = thisInstance.firstIdentifierOf(Subs.class);
					if (subsInstance != null) {
						SubsKey subKey = InstanceIdentifier.keyOf(subsInstance);
						if (subKey != null) {
							String subId = getIpAddressStr(subKey.getSubId());
							gatePathMap.put("subId", subId);
						}
					}
					InstanceIdentifier<Gates> gatesInstance = thisInstance.firstIdentifierOf(Gates.class);
					if (gatesInstance != null) {
						GatesKey gateKey = InstanceIdentifier.keyOf(gatesInstance);
						if (gateKey != null) {
							String gateId = gateKey.getGateId();
							gatePathMap.put("gateId", gateId);
						}
					}
				}
			} catch (ClassCastException err) {
				// do nothing, failure is ok
			}
		}

		private void getCcap(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.debug("onDataChanged().getCcap(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Ccaps) {
		            ccap = (Ccaps)entry.getValue();
		        }
		    }
		}

		private void getGates(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.debug("onDataChanged().getGates(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Gates) {
					Gates gate = (Gates)entry.getValue();
					InstanceIdentifier<?> iid = entry.getKey();
					getGatePathMap(iid);
					gateList.add(gate);
				}
		    }
		}
	}

	private String getIpAddressStr(IpAddress ipAddress) {
		String ipAddr = "";
		Ipv4Address ipv4 = ipAddress.getIpv4Address();
		Ipv6Address ipv6 = ipAddress.getIpv6Address();
		ipAddr = ipv4.getValue();
		if (ipAddr == "") {
			ipAddr = ipv6.getValue();
		}
		return ipAddr;
	}


	private enum ChangeAction {created, updated, removed};

	@Override
	public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		Map<InstanceIdentifier<?>, DataObject> createdData = change.getCreatedData();
		Map<InstanceIdentifier<?>, DataObject> updatedData = change.getUpdatedData();
		Map<InstanceIdentifier<?>, DataObject> originalData = change.getOriginalData();
		Set<InstanceIdentifier<?>> removedData = change.getRemovedPaths();
		DataObject originalSubtree = change.getOriginalSubtree();
		DataObject updatedSubtree = change.getUpdatedSubtree();
		try {
			// this will also validate all the values passed in the tree
			logger.debug("onDataChanged().createdData: " + createdData);
			logger.debug("onDataChanged().updatedData: " + updatedData);
			logger.debug("onDataChanged().originalData: " + originalData);
			logger.debug("onDataChanged().removedData: " + removedData);
			logger.debug("onDataChanged().originalSubtree: " + originalSubtree);
			logger.debug("onDataChanged().updatedSubtree: " + updatedSubtree);
		} catch (IllegalArgumentException | IllegalStateException err) {
			logger.warn("onDataChanged().change: Illegal Element Value in json object: " + err);
			return;
		}

		// Determine what change action took place by looking at the change object's InstanceIdentifier sets
		ChangeAction changeAction = null;
		InstanceData thisData = null;
		if (! createdData.isEmpty()) {
			changeAction = ChangeAction.created;
			thisData = new InstanceData(createdData);
		} else if (! removedData.isEmpty()) {
			changeAction = ChangeAction.removed;
			thisData = new InstanceData(removedData);
		} else if (! updatedData.isEmpty()) {
				changeAction = ChangeAction.updated;
				thisData = new InstanceData(updatedData);
		} else {
				// we should not be here -- complain bitterly and return
				logger.error("onDataChanged(): Unknown change action: " + change);
				return;
		}

		// select the change action
		String ccapId = null;
		Ccaps lastCcap = null;
		Ccaps thisCcap = null;
		Gates lastGate = null;
		Gates thisGate = null;
		switch (changeAction) {
		case created:
			// get the CMTS parameters from the CmtsNode in the Map entry (if new CMTS)
			thisCcap = thisData.ccap;
			if (thisCcap != null) {
				// get the CMTS node identity from the Instance Data
				ccapId = thisData.ccapId;
				logger.info("onDataChanged(): created CCAP: " + thisData.gatePath + "/" + thisCcap);
				ccapMap.put(ccapId, thisCcap);
				pcmmService.addCcap(thisCcap);
			}
			// get the PCMM gate parameters from the cmtsId/appId/subId/gateId path in the Maps entry (if new gate)
			for (Gates gate : thisData.gateList) {
				String gateId = gate.getGateId();
				String gatePathStr = thisData.gatePath + "/" + gateId ;
				gateMap.put(gatePathStr, gate);
				pcmmService.sendGateSet(gatePathStr, gate);
				logger.info("onDataChanged(): created QoS gate: " + gateId + " @ " + gatePathStr + "/" + gate);
			}
			break;
		case updated:
			thisCcap = thisData.ccap;
			if (thisCcap != null) {
				// get the CMTS node identity from the Instance Data
				ccapId = thisData.ccapId;
				lastCcap = ccapMap.get(ccapId);
				logger.info("onDataChanged(): updated CCAP " + ccapId + ": FROM: " + lastCcap + " TO: " + thisCcap);
				ccapMap.put(ccapId, thisCcap);
				// remove original cmtsNode
				pcmmService.removeCcap(lastCcap);
				// and add back the new one
				pcmmService.addCcap(thisCcap);
			}
			for (Gates gate : thisData.gateList) {
				String gateId = gate.getGateId();
				String gatePathStr = thisData.gatePath + "/" + gateId ;
				lastGate = gateMap.get(gatePathStr);
				logger.info("onDataChanged(): updated QoS gate: FROM: " + gatePathStr + "/" + lastGate + " TO: " + gate);
			}
			break;
		case removed:
			// remove gates before removing CMTS
			for (String gatePathStr: thisData.removePathList) {
				if (gateMap.containsKey(gatePathStr)) {
					lastGate = gateMap.remove(gatePathStr);
					pcmmService.sendGateDelete(gatePathStr);
					logger.info("onDataChanged(): removed QoS gate: " + gatePathStr + "/" + lastGate);
				}
			}
			for (String ccapIdStr: thisData.removePathList) {
				if (ccapMap.containsKey(ccapIdStr)) {
					thisCcap = ccapMap.remove(ccapIdStr);
					logger.info("onDataChanged(): removed CCAP " + ccapIdStr + "/" + thisCcap);
					ccapMap.remove(ccapIdStr);
					pcmmService.removeCcap(thisCcap);
				}
			}
			break;
		default:
			// we should not be here -- complain bitterly and return
			logger.error("onDataChanged(): Unknown switch change action: " + change);
			return;
		}
	}


}
