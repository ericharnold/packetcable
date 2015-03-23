package org.opendaylight.controller.packetcable.provider;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
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
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Ccaps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.CcapsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Qos;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceClassName;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceFlowDirection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ccap.attributes.AmId;
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
import org.pcmm.gates.ISubscriberID;
import org.pcmm.gates.ITrafficProfile;
import org.pcmm.gates.impl.GateID;
import org.pcmm.gates.impl.PCMMGateReq;
import org.pcmm.gates.impl.SubscriberID;
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

	private Map<String, Ccaps> ccapMap = new ConcurrentHashMap<String, Ccaps>();
	private Map<String, Gates> gateMap = new ConcurrentHashMap<String, Gates>();
	private Map<String, Ccaps> gateCcapMap = new ConcurrentHashMap<String, Ccaps>();
	private Map<Subnet, Ccaps> subscriberSubnetsMap = new ConcurrentHashMap<Subnet, Ccaps>();
	private Map<ServiceClassName, List<Ccaps>> downstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private Map<ServiceClassName, List<Ccaps>> upstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private PcmmService pcmmService;

	// keys to the /restconf/config/packetcable:ccaps and /restconf/config/packetcable:qos config datastore
	public static final InstanceIdentifier<Ccaps> ccapsIID = InstanceIdentifier.builder(Ccaps.class).build();
	public static final InstanceIdentifier<Qos> qosIID = InstanceIdentifier.builder(Qos.class).build();

	public PacketcableProvider() {
		executor = Executors.newCachedThreadPool();
		pcmmService = new PcmmService();
	}

//	public void setNotificationProvider(final NotificationProviderService salService) {
//		this.notificationProvider = salService;
//	}

	public void setDataBroker(final DataBroker salDataBroker) {
		this.dataBroker = salDataBroker;
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

	private String getIpAddressStr(IpAddress ipAddress) {
		String ipAddressStr = null;
		Ipv4Address ipv4 = ipAddress.getIpv4Address();

		if (ipv4 != null) {
			ipAddressStr = ipv4.getValue();
		} else {
			Ipv6Address ipv6 = ipAddress.getIpv6Address();
			ipAddressStr = ipv6.getValue();
		}
		return ipAddressStr;
	}

	private String getIpPrefixStr(IpPrefix ipPrefix) {
		String ipPrefixStr = null;
		Ipv4Prefix ipv4 = ipPrefix.getIpv4Prefix();
		if (ipv4 != null) {
			ipPrefixStr = ipv4.getValue();
		} else {
			Ipv6Prefix ipv6 = ipPrefix.getIpv6Prefix();
			ipPrefixStr = ipv6.getValue();
		}
		return ipPrefixStr;
	}

	private void updateCcapMaps(Ccaps ccap) {
		// add ccap to the subscriberSubnets map
		for (IpPrefix ipPrefix : ccap.getSubscriberSubnets()) {
			Subnet subnet = null;
			try {
				subnet = Subnet.createInstance(getIpPrefixStr(ipPrefix));
			} catch (UnknownHostException e) {
				logger.error("updateSubscriberSubnets: {}:{} FAILED: {}", ipPrefix, ccap, e.getMessage());
			}
			subscriberSubnetsMap.put(subnet, ccap);
		}
		// ccap to upstream SCN map
		for (ServiceClassName scn : ccap.getUpstreamScns()) {
			if (upstreamScnMap.containsKey(scn)) {
				upstreamScnMap.get(scn).add(ccap);
			} else {
				List<Ccaps> ccapList = new ArrayList<Ccaps>();
				ccapList.add(ccap);
				upstreamScnMap.put(scn, ccapList);
			}
		}
		// ccap to downstream SCN map
		for (ServiceClassName scn : ccap.getDownstreamScns()) {
			if (downstreamScnMap.containsKey(scn)) {
				downstreamScnMap.get(scn).add(ccap);
			} else {
				List<Ccaps> ccapList = new ArrayList<Ccaps>();
				ccapList.add(ccap);
				downstreamScnMap.put(scn, ccapList);
			}
		}
	}

	private Ccaps getSubscriberSubnetsLPM(IpAddress ipAddress) {
		String ipAddressStr = getIpAddressStr(ipAddress);
		InetAddress inetAddr = null;
		try {
			inetAddr = InetAddress.getByName(ipAddressStr);
		} catch (UnknownHostException e) {
			logger.error("getSubscriberSubnetsLPM: {} FAILED: {}", ipAddressStr, e.getMessage());
		}
		Ccaps matchedCcap = null;
		int longestPrefixLen = -1;
		for (Map.Entry<Subnet, Ccaps> entry : subscriberSubnetsMap.entrySet()) {
			Subnet subnet = entry.getKey();
			Ccaps thisCcap = entry.getValue();
			if (subnet.isInNet(inetAddr)) {
				int prefixLen = subnet.getPrefixLen();
				if (prefixLen > longestPrefixLen) {
					matchedCcap = thisCcap;
					longestPrefixLen = prefixLen;
				}
			}
		}
		return matchedCcap;
	}

	private ServiceFlowDirection findScnOnCcap(ServiceClassName scn, Ccaps ccap) {
		if (upstreamScnMap.containsKey(scn)) {
			List<Ccaps> ccapList = upstreamScnMap.get(scn);
			if (ccapList.contains(ccap)) {
				return ServiceFlowDirection.Us;
			}
		} else if (downstreamScnMap.containsKey(scn)) {
			List<Ccaps> ccapList = downstreamScnMap.get(scn);
			if (ccapList.contains(ccap)) {
				return ServiceFlowDirection.Ds;
			}
		}
		return null;
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
        	// and save it back to the gateRequest object for gate delete later
    		gateReq.setGateID(pcmmSender.getGateID());
			return true;
        }

        public Boolean sendGateDelete(PCMMGateReq gateReq) {
            logger.info("CcapClient: sendGateDelete(): {}:{} => {}", ipv4, port, gateReq);
        	try {
                pcmmSender = new PCMMPdpMsgSender(PCMMDef.C_PCMM, pcmmPdp.getClientHandle(), pcmmPdp.getSocket());
				pcmmSender.sendGateDelete(gateReq);
			} catch (COPSPdpException e) {
                logger.error("CcapClient: sendGateDelete(): {}:{} => {} FAILED: {}", ipv4, port, gateReq, e.getMessage());
			}
			return true;
        }
	}

	private class PcmmService {
		private Map<Ccaps, CcapClient> ccapClients = Maps.newConcurrentMap();
		private Map<String, PCMMGateReq> gateRequests = Maps.newConcurrentMap();

		public PcmmService() {
		}

		public void addCcap(Ccaps ccap) {
			String ccapId = ccap.getCcapId();
			IpAddress ipAddr = ccap.getConnection().getIpAddress();
			PortNumber port = ccap.getConnection().getPort();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("addCcap() {} @ {}: ", ccapId, ipv4);
			CcapClient client = new CcapClient();
			client.connect(ipAddr, port);
			if (client.isConnected) {
				ccapClients.put(ccap, client);
				logger.info("addCcap(): connected: {} @ {}", ccapId, ipv4);
			}
		}

		public void removeCcap(Ccaps ccap) {
			String ccapId = ccap.getCcapId();
			IpAddress ipAddr = ccap.getConnection().getIpAddress();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("removeCcap() {} @ {}: ", ccapId, ipv4);
			if (ccapClients.containsKey(ccap)) {
				CcapClient client = ccapClients.remove(ccap);
				client.disconnect();
				logger.info("removeCcap(): disconnected: {} @ {}", ccapId, ipv4);
			}
		}

		public Boolean sendGateSet(Ccaps ccap, String gatePathStr, IpAddress qosSubId, Gates qosGate) {
			CcapClient ccapClient = ccapClients.get(ccap);
			// assemble the gate request for this subId
			PCMMGateReqBuilder gateBuilder = new PCMMGateReqBuilder();
			gateBuilder.build(ccap.getAmId());
			gateBuilder.build(getIpAddressStr(qosSubId));
			// validate SCN exists on CCAP and force gateSpec.Direction to align with SCN direction
			ServiceClassName scn = qosGate.getTrafficProfile().getServiceClassName();
			if (scn != null) {
				ServiceFlowDirection scnDir = findScnOnCcap(scn, ccap);
				if (scnDir != null) {
					gateBuilder.build(qosGate.getGateSpec(), scnDir);
				} else {
	                logger.error("PCMMService: sendGateSet(): SCN {} not found on CCAP {} for {}/{}",
	                		scn, ccap, gatePathStr, qosGate);
	                return false;
				}
			} else {
				// not an SCN gate
				gateBuilder.build(qosGate.getGateSpec(), null);
			}
			gateBuilder.build(qosGate.getTrafficProfile());

			// pick a classifier type (only one for now)
			if (qosGate.getClassifier() != null) {
				gateBuilder.build(qosGate.getClassifier());
			} else if (qosGate.getExtClassifier() != null) {
				gateBuilder.build(qosGate.getExtClassifier());
			} else if (qosGate.getIpv6Classifier() != null) {
				gateBuilder.build(qosGate.getIpv6Classifier());
			}
			// assemble the final gate request
			PCMMGateReq gateReq = gateBuilder.getGateReq();
			// and remember it
			gateRequests.put(gatePathStr, gateReq);
			// and send it to the CCAP
			boolean ret = ccapClient.sendGateSet(gateReq);
			// and wait for the response to complete
			try {
				synchronized(gateReq) {
					gateReq.wait(1000);
				}
			} catch (InterruptedException e) {
                logger.error("PCMMService: sendGateSet(): gate response timeout exceeded for {}/{}",
                		gatePathStr, gateReq);
			}
			if (gateReq.getError() != null) {
				logger.warn("PCMMService: sendGateSet(): returned error: {}", gateReq.getError().toString());
				return false;
			} else {
				if (gateReq.getGateID() != null) {
					logger.info(String.format("PCMMService: sendGateSet(): returned GateId %08x: ", gateReq.getGateID().getGateID()));
				} else {
					logger.info("PCMMService: sendGateSet(): no gateId returned:");
				}
				return true;
			}
		}

		public Boolean sendGateDelete(Ccaps ccap, String gatePathStr) {
			CcapClient ccapClient = ccapClients.get(ccap);
			// recover the original gate request
			PCMMGateReq gateReq = gateRequests.remove(gatePathStr);
			if (gateReq != null) {
				Boolean ret = ccapClient.sendGateDelete(gateReq);
				// and wait for the response to complete
				try {
					synchronized(gateReq) {
						gateReq.wait(1000);
					}
				} catch (InterruptedException e) {
	                logger.error("PCMMService: sendGateDelete(): gate response timeout exceeded for {}/{}",
	                		gatePathStr, gateReq);
				}
				if (gateReq.getError() != null) {
					logger.warn("PCMMService: sendGateDelete(): returned error: {}", gateReq.getError().toString());
					return false;
				} else {
					if (gateReq.getGateID() != null) {
						logger.info(String.format("PCMMService: sendGateDelete(): deleted GateId %08x: ", gateReq.getGateID().getGateID()));
					} else {
						logger.info("PCMMService: sendGateDelete(): deleted but no gateId returned");
					}
					return true;
				}
			} else {
				return false;
			}
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
		// CCAP Identity
		public Ccaps ccap;
		public String ccapId;
		// Gate Identity
		public Map<String, String> gatePathMap = new HashMap<String, String>();
		public String gatePath;
		public List<Gates> gateList = new ArrayList<Gates>();
		public IpAddress subId;
		// remove path for either CCAP or Gates
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
							subId = subKey.getSubId();
							String subIdStr = getIpAddressStr(subId);
							gatePathMap.put("subId", subIdStr);
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
				updateCcapMaps(thisCcap);
			} else {
				// get the PCMM gate parameters from the cmtsId/appId/subId/gateId path in the Maps entry (if new gate)
				for (Gates gate : thisData.gateList) {
					String gateId = gate.getGateId();
					String gatePathStr = thisData.gatePath + "/" + gateId ;
					IpAddress subId = thisData.subId;
					thisCcap = getSubscriberSubnetsLPM(subId);
					if (thisCcap != null) {
						gateMap.put(gatePathStr, gate);
						gateCcapMap.put(gatePathStr, thisCcap);
						ccapId = thisCcap.getCcapId();
						if (pcmmService.sendGateSet(thisCcap, gatePathStr, subId, gate)) {
							logger.info("onDataChanged(): created QoS gate {} for {}/{}/{}: ", gateId, ccapId, gatePathStr, gate);
						} else {
							logger.error("onDataChanged(): Unable to create QoS gate {} for {}/{}/{}: ", gateId, ccapId, gatePathStr, gate);
						}
					} else {
						String subIdStr = thisData.gatePathMap.get("subId");
						logger.error("onDataChanged(): create QoS gate {} FAILED: no CCAP found for {}: @ {}/{} ", gateId, subIdStr, gatePathStr, gate);
					}
				}
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
			} else {
				for (Gates gate : thisData.gateList) {
					String gateId = gate.getGateId();
					String gatePathStr = thisData.gatePath + "/" + gateId ;
					lastGate = gateMap.get(gatePathStr);
					logger.info("onDataChanged(): updated QoS gate: FROM: " + gatePathStr + "/" + lastGate + " TO: " + gate);
				}
			}
			break;
		case removed:
			// remove gates before removing CMTS
			for (String gatePathStr: thisData.removePathList) {
				if (gateMap.containsKey(gatePathStr)) {
					thisGate = gateMap.remove(gatePathStr);
					String gateId = thisGate.getGateId();
					thisCcap = gateCcapMap.remove(gatePathStr);
					ccapId = thisCcap.getCcapId();
					pcmmService.sendGateDelete(thisCcap, gatePathStr);
					logger.info("onDataChanged(): removed QoS gate {} for {}/{}/{}: ", gateId, ccapId, gatePathStr, thisGate);
				}
			}
			for (String ccapIdStr: thisData.removePathList) {
				if (ccapMap.containsKey(ccapIdStr)) {
					thisCcap = ccapMap.remove(ccapIdStr);
					pcmmService.removeCcap(thisCcap);
					logger.info("onDataChanged(): removed CCAP " + ccapIdStr + "/" + thisCcap);
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
