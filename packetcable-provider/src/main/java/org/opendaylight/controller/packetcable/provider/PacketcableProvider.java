package org.opendaylight.controller.packetcable.provider;

import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.CcapsBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.CcapsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Qos;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceClassName;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceFlowDirection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.TosByte;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.TpProtocol;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ccap.attributes.AmId;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ccap.attributes.AmIdBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ccap.attributes.Connection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ccap.attributes.ConnectionBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.classifier.Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.classifier.ClassifierBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.ext.classifier.ExtClassifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.ext.classifier.ExtClassifierBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gate.spec.GateSpec;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gate.spec.GateSpecBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.AppsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.Subs;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.SubsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.Gates;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.GatesBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.GatesKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.ipv6.classifier.Ipv6Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.ipv6.classifier.Ipv6ClassifierBuilder;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.traffic.profile.TrafficProfile;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.traffic.profile.TrafficProfileBuilder;
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

	// keys to the /restconf/config/packetcable:ccaps and /restconf/config/packetcable:qos config datastore
	public static final InstanceIdentifier<Ccaps> ccapsIID = InstanceIdentifier.builder(Ccaps.class).build();
	public static final InstanceIdentifier<Qos> qosIID = InstanceIdentifier.builder(Qos.class).build();

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
	    String errMessage = null;

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
                isConnected = false;
                logger.error("CcapClient: connect(): {}:{} FAILED: {}", ipv4, port, e.getMessage());
                errMessage = e.getMessage();
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

		public String addCcap(Ccaps ccap) {
			String ccapId = ccap.getCcapId();
			IpAddress ipAddr = ccap.getConnection().getIpAddress();
			PortNumber portNum = ccap.getConnection().getPort();
			int port = portNum.getValue();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("addCcap() {} @ {}:{}", ccapId, ipv4, port);
			CcapClient client = new CcapClient();
			client.connect(ipAddr, portNum);
			String response = null;
			if (client.isConnected) {
				ccapClients.put(ccap, client);
				logger.info("addCcap(): connected: {} @ {}:{}", ccapId, ipv4, port);
				response = String.format("200 OK - CCAP %s connected @ %s:%d", ccapId, ipv4, port);
			} else {
				response = String.format("404 Not Found - CCAP %s failed to connect @ %s:%d - %s",
											ccapId, ipv4, port, client.errMessage);
			}
			return response;
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

		public String sendGateSet(Ccaps ccap, String gatePathStr, IpAddress qosSubId, Gates qosGate) {
			String response = null;
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
	                		scn.getValue(), ccap, gatePathStr, qosGate);
					response = String.format("404 Not Found - SCN %s not found on CCAP %s for %s",
							scn.getValue(), ccap.getCcapId(), gatePathStr);
	                return response;
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
				response = String.format("408 Request Timeout - gate response timeout exceeded for %s/%s",
						ccap.getCcapId(), gatePathStr);
				return response;
			}
			if (gateReq.getError() != null) {
				logger.error("PCMMService: sendGateSet(): returned error: {}",
						gateReq.getError().toString());
				response = String.format("404 Not Found - sendGateSet for %s/%s returned error - %s",
						ccap.getCcapId(), gatePathStr, gateReq.getError().toString());
				return response;
			} else {
				if (gateReq.getGateID() != null) {
					logger.info(String.format("PCMMService: sendGateSet(): returned GateId %08x: ",
							gateReq.getGateID().getGateID()));
					response = String.format("200 OK - sendGateSet for %s/%s returned GateId %08x",
							ccap.getCcapId(), gatePathStr, gateReq.getGateID().getGateID());
				} else {
					logger.info("PCMMService: sendGateSet(): no gateId returned:");
					response = String.format("404 Not Found - sendGateSet for %s/%s no gateId returned",
							ccap.getCcapId(), gatePathStr);
				}
				return response;
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
		public InstanceIdentifier<Ccaps> ccapIID;
		// Gate Identity
		public IpAddress subId;
		public Map<String, String> gatePathMap = new HashMap<String, String>();
		public String gatePath;
		public Map<InstanceIdentifier<Gates>, Gates> gateIidMap = new HashMap<InstanceIdentifier<Gates>, Gates>();
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
				if (! gateIidMap.isEmpty()){
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
		            ccapIID = (InstanceIdentifier<Ccaps>) entry.getKey();
		        }
		    }
		}

		private void getGates(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.debug("onDataChanged().getGates(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Gates) {
					Gates gate = (Gates)entry.getValue();
					InstanceIdentifier<Gates> gateIID = (InstanceIdentifier<Gates>)entry.getKey();
					getGatePathMap(gateIID);
					gateIidMap.put(gateIID, gate);
				}
		    }
		}
	}

	private class Response implements Runnable {

		private String message = null;
		private InstanceIdentifier<Ccaps> ccapIID = null;
		private Ccaps ccapBase = null;
		private InstanceIdentifier<Gates> gateIID = null;
		private Gates gateBase = null;

		public Response(InstanceIdentifier<Ccaps> ccapIID, Ccaps ccapBase, String message) {
			this.ccapIID = ccapIID;
			this.ccapBase = ccapBase;
			this.message = message;
		}
		public Response(InstanceIdentifier<Gates> gateIID, Gates gateBase, String message) {
			this.gateIID = gateIID;
			this.gateBase = gateBase;
			this.message = message;
		}
		public void setResponse(InstanceIdentifier<Ccaps> ccapIID, Ccaps ccapBase, String message) {
			CcapsBuilder ccapBuilder = new CcapsBuilder(ccapBase);
			ccapBuilder.setResponse(message);
			Ccaps ccap = ccapBuilder.build();
	        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
	        writeTx.merge(LogicalDatastoreType.CONFIGURATION, ccapIID, ccap, true);
	        writeTx.commit();
	        logger.info("Response.setResponse(ccap) complete {} {} {}", message, ccap, ccapIID);
		}
		public void setResponse(InstanceIdentifier<Gates> gateIID, Gates gateBase, String message) {
			GatesBuilder gateBuilder = new GatesBuilder(gateBase);
			gateBuilder.setResponse(message);
			Gates gate = gateBuilder.build();
	        WriteTransaction writeTx = dataBroker.newWriteOnlyTransaction();
	        writeTx.merge(LogicalDatastoreType.CONFIGURATION, gateIID, gate, true);
	        writeTx.commit();
	        logger.info("Response.setResponse(gate) complete: {} {} {}", message, gate, gateIID);
		}
		@Override
		public void run() {
			if (ccapIID != null) {
				setResponse(ccapIID, ccapBase, message);
			} else if (gateIID != null) {
				setResponse(gateIID, gateBase, message);
			} else {
				logger.error("Unknown Response: must be for a CCAP or Gate instance");
			}
		}
	}

	private class ValidateInstanceData {

		// CCAP Identity
		public Ccaps ccap;
		public InstanceIdentifier<Ccaps> ccapIID;
		// Gate Identities
		public Map<InstanceIdentifier<Gates>, Gates> gateIidMap = new HashMap<InstanceIdentifier<Gates>, Gates>();

		public ValidateInstanceData(Map<InstanceIdentifier<?>, DataObject> thisData) {
			getCcap(thisData);
			if (ccap == null) {
				getGates(thisData);
			}
		}
		public boolean isResponseEcho() {
			// see if there is a response object in the updated data
			// if so this is an echo of the response message insertion so our caller can exit right away
			if (ccap != null && ccap.getResponse() != null) {
				return true;
			} else if (! gateIidMap.isEmpty() && gateIidMap.values().iterator().next().getResponse() != null) {
				return true;
			}
			return false;
		}
		public boolean validateYang() {
			String message = "";
			boolean valid = true;
			if (isResponseEcho()) {
				// don't validiate the echo again
				return valid;
			}
			if (ccap != null) {
				message = "400 Bad Request - Invalid Element Values in json object - ";
				Response response = new Response(ccapIID, ccap, message);
				if (! validateCcap(ccap, response)) {
					logger.error("Validate CCAP {} failed - {}", ccap.getCcapId(), response.message);
					executor.execute(response);
					valid = false;
				}
			} else if (! gateIidMap.isEmpty()) {
				for (Map.Entry<InstanceIdentifier<Gates>, Gates> entry : gateIidMap.entrySet()) {
					InstanceIdentifier<Gates> gateIID = entry.getKey();
					Gates gate = entry.getValue();
					message = "400 Bad Request - Invalid Element Values in json object - ";
					Response response = new Response(gateIID, gate, message);
					if (! validateGate(gate, response)) {
						logger.error("Validate Gate {} failed - {}", gate.getGateId(), response.message);
						executor.execute(response);
						valid = false;
					}
				}
			}
			return valid;
		}
		private void getCcap(Map<InstanceIdentifier<?>, DataObject> thisData) {
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Ccaps) {
		            ccap = (Ccaps)entry.getValue();
		            ccapIID = (InstanceIdentifier<Ccaps>) entry.getKey();
		        }
		    }
		}
		private void getGates(Map<InstanceIdentifier<?>, DataObject> thisData) {
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Gates) {
					Gates gate = (Gates)entry.getValue();
					InstanceIdentifier<Gates> gateIID = (InstanceIdentifier<Gates>)entry.getKey();
					gateIidMap.put(gateIID, gate);
				}
		    }
		}
		private String validateMethod(Class thisClass, Object thisObj, String methodName) {
			String error = null;
			try {
				Method method = thisClass.getMethod(methodName);
				Object result = method.invoke(thisObj);
			} catch (IllegalArgumentException e) {
				error = e.getMessage();
				return error;
			} catch (Exception e) {
				error = " ";
//				error = String.format("%s.%s(): Method failed: %s ", thisClass.getSimpleName(), methodName, e.getMessage());
				return error;
			}
			return error;

		}
		private String validateObj(Object obj) {
			String error = null;
			try {
				obj.toString();
			} catch (IllegalArgumentException e) {
				error = e.getMessage();
				return error;
			} catch (Exception e) {
				error = "toString() failed: " + e.getMessage();
				return error;
			}
			return error;
		}

		private boolean validateGate(Gates gate, Response response) {
			// validate gate and null out invalid elements as we go
			String message = "";
			String error = null;
			boolean valid = true;
			boolean rebuild = false;
			// gate-spec
			GateSpec gateSpec = gate.getGateSpec();;
			if (gateSpec != null) {
				ServiceFlowDirection dir = null;
				error = validateMethod(GateSpec.class, gateSpec, "getDirection");
				if (error == null) {
					dir = gateSpec.getDirection();
					if (dir != null) {
						if (gate.getTrafficProfile().getServiceClassName() != null) {
							message += " gate-spec.direction not allowed for traffic-profile.SCN;";
							valid = false;
						}
					}
				} else {
					message += " gate-spec.direction invalid: must be 'us' or 'ds' -" + error;
					dir = null;
					valid = false;
				}
				TosByte tosByte = null;
				error = validateMethod(GateSpec.class, gateSpec, "getDscpTosOverwrite");
				if (error == null) {
					tosByte = gateSpec.getDscpTosOverwrite();
				} else {
					message += " gate-spec.dscp-tos-overwrite invalid: " + error;
					tosByte = null;
					valid = false;
				}
				TosByte tosMask = null;
				error = validateMethod(GateSpec.class, gateSpec, "getDscpTosMask");
				if (error == null) {
					tosMask = gateSpec.getDscpTosMask();
					if (tosByte != null && tosMask == null) {
						message += " gate-spec.dscp-tos-mask missing;";
						valid = false;
					}
				} else {
					message += " gate-spec.dscp-tos-mask invalid: " + error;
					tosMask = null;
					valid = false;
				}
				if (! valid) {
					GateSpecBuilder gateSpecBuilder = new GateSpecBuilder();
					gateSpecBuilder.setDirection(dir);
					gateSpecBuilder.setDscpTosOverwrite(tosByte);
					gateSpecBuilder.setDscpTosMask(tosMask);
					gateSpec = gateSpecBuilder.build();
					rebuild = true;
				}
			}
			// traffic-profile
			error = null;
			valid = true;
			TrafficProfile profile = gate.getTrafficProfile();
			if (profile == null) {
				message += " traffic-profile is required;";
				rebuild = true;
			} else {
				ServiceClassName scn = null;
				error = validateMethod(TrafficProfile.class, profile, "getServiceClassName");
				if (error == null) {
					scn = profile.getServiceClassName();
					if (scn == null) {
						message += " traffic-profile.service-class-name missing;";
						valid = false;
					}
				} else {
					message += " traffic-profile.service-class-name invalid: must be 2-16 characters " + error;
					scn = null;
					valid = false;
				}
				if (! valid) {
					TrafficProfileBuilder profileBuilder = new TrafficProfileBuilder();
					profileBuilder.setServiceClassName(scn);
					profile = profileBuilder.build();
					rebuild = true;
				}
			}
			// classifiers (one of legacy classifier, ext-classifier, or ipv6 classifier
			Classifier classifier = gate.getClassifier();
			ExtClassifier extClassifier = gate.getExtClassifier();
			Ipv6Classifier ipv6Classifier = gate.getIpv6Classifier();
			valid = true;
			int count = 0;
			if (classifier != null) { count++; }
			if (extClassifier != null) { count++; }
			if (ipv6Classifier != null) { count++; }
			if (count == 0){
				message += " Missing classifer: must have only 1 of classifier, ext-classifier, or ipv6-classifier";
				rebuild = true;
			} else if (count > 1) {
				message += " Multiple classifiers: must have only 1 of classifier, ext-classifier, or ipv6-classifier";
				rebuild = true;
			} else if (count == 1) {
				if (classifier != null) {
					// validate classifier
					count = 0;
					Ipv4Address sip = null;
					error = validateMethod(Classifier.class, classifier, "getSrcIp");
					if (error == null) {
						sip = classifier.getSrcIp();
						count++;
					} else {
						message += " classifier.srcIp invalid: - " + error;
						sip = null;
						valid = false;
					}
					Ipv4Address dip = null;
					error = validateMethod(Classifier.class, classifier, "getDstIp");
					if (error == null) {
						dip = classifier.getDstIp();
						count++;
					} else {
						message += " classifier.dstIp invalid: - " + error;
						dip = null;
						valid = false;
					}
					TpProtocol proto = null;
					error = validateMethod(Classifier.class, classifier, "getProtocol");
					if (error == null) {
						proto = classifier.getProtocol();
						count++;
					} else {
						message += " classifier.protocol invalid: - " + error;
						proto = null;
						valid = false;
					}
					PortNumber sport = null;
					error = validateMethod(Classifier.class, classifier, "getSrcPort");
					if (error == null) {
						sport = classifier.getSrcPort();
						count++;
					} else {
						message += " classifier.srcPort invalid: - " + error;
						sport = null;
						valid = false;
					}
					PortNumber dport = null;
					error = validateMethod(Classifier.class, classifier, "getDstPort");
					if (error == null) {
						dport = classifier.getDstPort();
						count++;
					} else {
						message += " classifier.dstPort invalid: - " + error;
						dport = null;
						valid = false;
					}
					TosByte tosByte = null;
					error = validateMethod(Classifier.class, classifier, "getTosByte");
					if (error == null) {
						tosByte = classifier.getTosByte();
						count++;
					} else {
						message += " classifier.tosByte invalid: " + error;
						tosByte = null;
						valid = false;
					}
					TosByte tosMask = null;
					error = validateMethod(Classifier.class, classifier, "getTosMask");
					if (error == null) {
						tosMask = classifier.getTosMask();
						if (tosByte != null && tosMask == null) {
							message += " classifier.tosMask missing;";
							valid = false;
						}
					} else {
						message += " classifier.tosMask invalid: " + error;
						tosMask = null;
						valid = false;
					}
					if (count == 0) {
						message += " classifer must have at least one match field";
						valid = false;
					}
					if (! valid) {
						ClassifierBuilder cBuilder = new ClassifierBuilder();
						cBuilder.setSrcIp(sip);
						cBuilder.setDstIp(dip);
						cBuilder.setProtocol(proto);
						cBuilder.setSrcPort(sport);
						cBuilder.setDstPort(dport);
						cBuilder.setTosByte(tosByte);
						cBuilder.setTosMask(tosMask);
						classifier = cBuilder.build();
						rebuild = true;
					}
				} else if (extClassifier != null) {
					//validate ext-classifier
					count = 0;
					Ipv4Address sip = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getSrcIp");
					if (error == null) {
						sip = extClassifier.getSrcIp();
						count++;
					} else {
						message += " ext-classifier.srcIp invalid: - " + error;
						sip = null;
						valid = false;
					}
					Ipv4Address sipMask = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getSrcIpMask");
					if (error == null) {
						sipMask = extClassifier.getSrcIpMask();
						count++;
					} else {
						message += " ext-classifier.srcIpMask invalid: - " + error;
						sipMask = null;
						valid = false;
					}
					if (sip != null && sipMask == null) {
						message += " ext-classifier.srcIpMask missing";
						valid = false;
					}
					Ipv4Address dip = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getDstIp");
					if (error == null) {
						dip = extClassifier.getDstIp();
						count++;
					} else {
						message += " ext-classifier.dstIp invalid: - " + error;
						dip = null;
						valid = false;
					}
					Ipv4Address dipMask = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getDstIpMask");
					if (error == null) {
						dipMask = extClassifier.getDstIpMask();
						count++;
					} else {
						message += " ext-classifier.srcIpMask invalid: - " + error;
						dipMask = null;
						valid = false;
					}
					if (dip != null && dipMask == null) {
						message += " ext-classifier.dstIpMask missing;";
						valid = false;
					}
					TpProtocol proto = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getProtocol");
					if (error == null) {
						proto = extClassifier.getProtocol();
						count++;
					} else {
						message += " ext-classifier.protocol invalid: - " + error;
						proto = null;
						valid = false;
					}
					PortNumber sportStart = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getSrcPortStart");
					if (error == null) {
						sportStart = extClassifier.getSrcPortStart();
						count++;
					} else {
						message += " ext-classifier.srcPortStart invalid: - " + error;
						sportStart = null;
						valid = false;
					}
					PortNumber sportEnd = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getSrcPortEnd");
					if (error == null) {
						sportEnd = extClassifier.getSrcPortEnd();
						count++;
					} else {
						message += " ext-classifier.srcPortEnd invalid: - " + error;
						sportEnd = null;
						valid = false;
					}
					if (sportStart != null && sportEnd != null) {
						if (sportStart.getValue() > sportEnd.getValue()) {
							message += " ext-classifier.srcPortStart greater than srcPortEnd";
							valid = false;
						}
					}
					PortNumber dportStart = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getDstPortStart");
					if (error == null) {
						dportStart = extClassifier.getDstPortStart();
						count++;
					} else {
						message += " ext-classifier.dstPortStart invalid: - " + error;
						dportStart = null;
						valid = false;
					}
					PortNumber dportEnd = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getDstPortEnd");
					if (error == null) {
						dportEnd = extClassifier.getDstPortEnd();
						count++;
					} else {
						message += " ext-classifier.dstPortEnd invalid: - " + error;
						dportEnd = null;
						valid = false;
					}
					if (dportStart != null && dportEnd != null) {
						if (dportStart.getValue() > dportEnd.getValue()) {
							message += " ext-classifier.dstPortStart greater than dstPortEnd";
							valid = false;
						}
					}
					TosByte tosByte = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getTosByte");
					if (error == null) {
						tosByte = extClassifier.getTosByte();
						count++;
					} else {
						message += " ext-classifier.tosByte invalid: " + error;
						tosByte = null;
						valid = false;
					}
					TosByte tosMask = null;
					error = validateMethod(ExtClassifier.class, extClassifier, "getTosMask");
					if (error == null) {
						tosMask = extClassifier.getTosMask();
						if (tosByte != null && tosMask == null) {
							message += " ext-classifier.tosMask missing;";
							valid = false;
						}
					} else {
						message += " ext-classifier.tosMask invalid: " + error;
						tosMask = null;
						valid = false;
					}
					if (count == 0) {
						message += " ext-classifer must have at least one match field";
						valid = false;
					}
					if (! valid) {
						ExtClassifierBuilder cBuilder = new ExtClassifierBuilder();
						cBuilder.setSrcIp(sip);
						cBuilder.setSrcIpMask(sipMask);
						cBuilder.setDstIp(dip);
						cBuilder.setDstIpMask(dipMask);
						cBuilder.setProtocol(proto);
						cBuilder.setSrcPortStart(sportStart);
						cBuilder.setSrcPortEnd(sportEnd);
						cBuilder.setDstPortStart(dportStart);
						cBuilder.setDstPortEnd(dportEnd);;
						cBuilder.setTosByte(tosByte);
						cBuilder.setTosMask(tosMask);
						extClassifier = cBuilder.build();
						rebuild = true;
					}
				} else if (ipv6Classifier != null) {
					// validate ipv6-classifier
					count = 0;
					Ipv6Prefix sip6 = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getSrcIp6");
					if (error == null) {
						sip6 = ipv6Classifier.getSrcIp6();
						count++;
					} else {
						message += " ipv6-classifier.srcIp invalid: - " + error;
						sip6 = null;
						valid = false;
					}
					Ipv6Prefix dip6 = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getDstIp6");
					if (error == null) {
						dip6 = ipv6Classifier.getDstIp6();
						count++;
					} else {
						message += " ipv6-classifier.dstIp invalid: - " + error;
						dip6 = null;
						valid = false;
					}
					Long flowLabel = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getFlowLabel");
					if (error == null) {
						flowLabel = ipv6Classifier.getFlowLabel();
						if (flowLabel > 1048575) {
							message += " ipv6-classifier.flowLabel invalid: - must be 0..1048575";
							flowLabel = null;
							valid = false;
						} else {
							count++;
						}
					} else {
						message += " ipv6-classifier.flowLabel invalid: - " + error;
						flowLabel = null;
						valid = false;
					}
					TpProtocol nxtHdr = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getNextHdr");
					if (error == null) {
						nxtHdr = ipv6Classifier.getNextHdr();
						count++;
					} else {
						message += " ipv6-classifier.nextHdr invalid: - " + error;
						nxtHdr = null;
						valid = false;
					}
					PortNumber sportStart = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getSrcPortStart");
					if (error == null) {
						sportStart = ipv6Classifier.getSrcPortStart();
						count++;
					} else {
						message += " ipv6-classifier.srcPortStart invalid: - " + error;
						sportStart = null;
						valid = false;
					}
					PortNumber sportEnd = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getSrcPortEnd");
					if (error == null) {
						sportEnd = ipv6Classifier.getSrcPortEnd();
						count++;
					} else {
						message += " ipv6-classifier.srcPortEnd invalid: - " + error;
						sportEnd = null;
						valid = false;
					}
					if (sportStart != null && sportEnd != null) {
						if (sportStart.getValue() > sportEnd.getValue()) {
							message += " ipv6-classifier.srcPortStart greater than srcPortEnd";
							valid = false;
						}
					}
					PortNumber dportStart = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getDstPortStart");
					if (error == null) {
						dportStart = ipv6Classifier.getDstPortStart();
						count++;
					} else {
						message += " ipv6-classifier.dstPortStart invalid: - " + error;
						dportStart = null;
						valid = false;
					}
					PortNumber dportEnd = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getDstPortEnd");
					if (error == null) {
						dportEnd = ipv6Classifier.getDstPortEnd();
						count++;
					} else {
						message += " ipv6-classifier.dstPortEnd invalid: - " + error;
						dportEnd = null;
						valid = false;
					}
					if (dportStart != null && dportEnd != null) {
						if (dportStart.getValue() > dportEnd.getValue()) {
							message += " ipv6-classifier.dstPortStart greater than dstPortEnd";
							valid = false;
						}
					}
					TosByte tcLow = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getTcLow");
					if (error == null) {
						tcLow = ipv6Classifier.getTcLow();
						count++;
					} else {
						message += " ipv6-classifier.tc-low invalid: " + error;
						tcLow = null;
						valid = false;
					}
					TosByte tcHigh = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getTcHigh");
					if (error == null) {
						tcHigh = ipv6Classifier.getTcHigh();
						count++;
					} else {
						message += " ipv6-classifier.tc-high invalid: " + error;
						tcHigh = null;
						valid = false;
					}
					if (tcLow != null && tcHigh != null) {
						if (tcLow.getValue() > tcHigh.getValue()) {
							message += " ipv6-classifier.tc-low is greater than tc-high";
							valid = false;
						}
					}
					TosByte tcMask = null;
					error = validateMethod(Ipv6Classifier.class, ipv6Classifier, "getTcMask");
					if (error == null) {
						tcMask = ipv6Classifier.getTcMask();
					} else {
						message += " ipv6-classifier.tc-mask invalid: " + error;
						tcMask = null;
						valid = false;
					}
					if (tcLow != null && tcHigh != null && tcMask == null) {
						message += " ipv6-classifier.tc-mask missing;";
						valid = false;
					}
					if (count == 0) {
						message += " ipv6-classifer must have at least one match field";
						valid = false;
					}
					if (! valid) {
						Ipv6ClassifierBuilder cBuilder = new Ipv6ClassifierBuilder();
						cBuilder.setSrcIp6(sip6);
						cBuilder.setDstIp6(dip6);
						cBuilder.setFlowLabel(flowLabel);
						cBuilder.setNextHdr(nxtHdr);
						cBuilder.setSrcPortStart(sportStart);
						cBuilder.setSrcPortEnd(sportEnd);
						cBuilder.setDstPortStart(dportStart);
						cBuilder.setDstPortEnd(dportEnd);;
						cBuilder.setTcLow(tcLow);
						cBuilder.setTcHigh(tcHigh);
						cBuilder.setTcMask(tcMask);
						ipv6Classifier = cBuilder.build();
						rebuild = true;
					}
				}
			}
			// rebuild the gate object with valid data and set the response
			if (rebuild) {
				GatesBuilder builder = new GatesBuilder();
				builder.setGateId(gate.getGateId());
				builder.setKey(gate.getKey());
				builder.setGateSpec(gateSpec);
				builder.setTrafficProfile(profile);
				if (classifier != null) {
					builder.setClassifier(classifier);
				} else if (extClassifier != null) {
					builder.setExtClassifier(extClassifier);
				} else if (ipv6Classifier != null) {
					builder.setIpv6Classifier(ipv6Classifier);
				}
				gate = builder.build();
				response.gateBase = gate;
				response.message += message;
			}
			return (! rebuild);
		}

		private boolean validateCcap(Ccaps ccap, Response response) {
			// validate ccap and null out invalid elements as we go
			String message = "";
			String error = null;
			boolean valid = true;
			boolean rebuild = false;
			// amId
			AmId amId = ccap.getAmId();
			if (amId == null) {
				message += " amId is required;";
				rebuild = true;
			} else {
				Integer amTag = null;
				error = validateMethod(AmId.class, amId, "getAmTag");
				if (error == null) {
					amTag = amId.getAmTag();
					if (amTag == null) {
						message += " amId.amTag missing;";
						valid = false;
					}
				} else {
					message += " amId.amTag invalid: " + error;
					amTag = null;
					valid = false;
				}
				Integer amType = null;
				error = validateMethod(AmId.class, amId, "getAmType");
				if (error == null) {
					amType = amId.getAmType();
					if (amType == null) {
						message += " amId.amType missing;";
						valid = false;
					}
				} else {
					message += " amId.amType invalid: " + error;
					amType = null;
					valid = false;
				}
				if (! valid) {
					AmIdBuilder amIdBuilder = new AmIdBuilder();
					amIdBuilder.setAmTag(amTag);
					amIdBuilder.setAmType(amType);
					amId = amIdBuilder.build();
					rebuild = true;
				}
			}
			// connection
			error = null;
			valid = true;
			Connection conn = ccap.getConnection();
			if (conn == null) {
				message += " connection is required;";
				rebuild = true;
			} else {
				IpAddress ipAddress = null;
				error = validateMethod(Connection.class, conn, "getIpAddress");
				if (error == null) {
					ipAddress = conn.getIpAddress();
					if (ipAddress == null) {
						message += " connection.ipAddress missing;";
						valid = false;
					}
				} else {
					message += " connection.ipAddress invalid: " + error;
					ipAddress = null;
					valid = false;
				}
				PortNumber portNum = null;
				error = validateMethod(Connection.class, conn, "getPort");
				if (error == null) {
					portNum = conn.getPort();
				} else {
					message += " connection.port invalid: " + error;
					portNum = null;
					valid = false;
				}
				if (! valid) {
					ConnectionBuilder connBuilder = new ConnectionBuilder();
					connBuilder.setIpAddress(ipAddress);
					connBuilder.setPort(portNum);
					conn = connBuilder.build();
					rebuild = true;
				}
			}
			// subscriber-subnets
			error = null;
			valid = true;
			List<IpPrefix> subnets = null;
			error = validateMethod(Ccaps.class, ccap, "getSubscriberSubnets");
			if (error == null) {
				subnets = ccap.getSubscriberSubnets();
				if (subnets == null) {
					message += " subscriber-subnets is required;";
					rebuild = true;
				}
			} else {
				message += " subscriber-subnets contains invalid IpPrefix - must be <ipaddress>/<prefixlen> format;" + error;
				rebuild = true;
			}
			// upstream-scns and downstream-scns
			error = null;
			valid = true;
			List<ServiceClassName> usScns = null;
			error = validateMethod(Ccaps.class, ccap, "getUpstreamScns");
			if (error == null) {
				usScns = ccap.getUpstreamScns();
				if (usScns == null) {
					message += " upstream-scns is required;";
					rebuild = true;
				}
			} else {
				message += " upstream-scns contains invalid SCN - must be 2-16 characters;" + error;
				rebuild = true;
			}
			error = null;
			valid = true;
			List<ServiceClassName> dsScns = null;
			error = validateMethod(Ccaps.class, ccap, "getDownstreamScns");
			if (error == null) {
				usScns = ccap.getDownstreamScns();
				if (usScns == null) {
					message += " downstream-scns is required;";
					rebuild = true;
				}
			} else {
				message += " downstream-scns contains invalid SCN - must be 2-16 characters;" + error;
				rebuild = true;
			}
			// rebuild the ccap object with valid data and set the response
			if (rebuild) {
				CcapsBuilder builder = new CcapsBuilder();
				builder.setCcapId(ccap.getCcapId());
				builder.setKey(ccap.getKey());
				builder.setAmId(amId);
				builder.setConnection(conn);
				builder.setSubscriberSubnets(subnets);
				builder.setUpstreamScns(usScns);
				builder.setDownstreamScns(dsScns);
				ccap = builder.build();
				response.ccapBase = ccap;
				response.message += message;
			}
			return (! rebuild);
		}




		private String validateYangObject(Object obj) {
			// recursively look for bad yang values and return a message string with clues
			String message = "";
			Method[] methods = obj.getClass().getDeclaredMethods();
			for (int i = 0; i < methods.length; i++) {
				String name = methods[i].getName();
				if (! name.startsWith("get")) continue;
				try {
					if (methods[i].getReturnType() == List.class) {
						List objList = (List) methods[i].invoke(obj);
						for (Object thisObj : objList) {
							String result = validateYangObject(thisObj);
							if (result != null) {
								message += name + ":[" + result + "]";
							}
						}
					} else {
						// toString will fail if the json value is not yang correct
						methods[i].invoke(obj).toString();
					}
				} catch (Exception err) {
					message += String.format(" %s", name);
				}
			}
			return message;
		}
	}

	private enum ChangeAction {created, updated, removed};

	@Override
	public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		Map<InstanceIdentifier<?>, DataObject> createdData = change.getCreatedData();
		Map<InstanceIdentifier<?>, DataObject> updatedData = change.getUpdatedData();
		Map<InstanceIdentifier<?>, DataObject> originalData = change.getOriginalData();
		Set<InstanceIdentifier<?>> removedData = change.getRemovedPaths();

		// Determine what change action took place by looking at the change object's InstanceIdentifier sets
		ValidateInstanceData validator = null;
		InstanceData thisData = null;
		ChangeAction changeAction = null;
		if (! createdData.isEmpty()) {
			validator = new ValidateInstanceData(createdData);
			if (! validator.validateYang()) {
				// leave now -- a bad yang object has been detected and a response object has been inserted
				return;
			}
			changeAction = ChangeAction.created;
			thisData = new InstanceData(createdData);
			logger.debug("onDataChanged().createdData: " + createdData);
		} else if (! removedData.isEmpty()) {
			changeAction = ChangeAction.removed;
			thisData = new InstanceData(removedData);
			logger.debug("onDataChanged().removedData: " + removedData);
//			logger.debug("onDataChanged().originalData: " + originalData);
		} else if (! updatedData.isEmpty()) {
			validator = new ValidateInstanceData(updatedData);
			if (validator.isResponseEcho()) {
				// leave now -- this is an echo of the inserted response object
				return;
			}
			changeAction = ChangeAction.updated;
			thisData = new InstanceData(updatedData);
			logger.debug("onDataChanged().updatedData: " + updatedData);
			logger.debug("onDataChanged().originalData: " + originalData);
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
			String message = null;
			if (thisCcap != null) {
				// get the CMTS node identity from the Instance Data
				ccapId = thisData.ccapId;
				message = pcmmService.addCcap(thisCcap);
				if (message.contains("200 OK")) {
					ccapMap.put(ccapId, thisCcap);
					updateCcapMaps(thisCcap);
					logger.info("onDataChanged(): created CCAP: {}/{} : {}", thisData.gatePath, thisCcap, message);
				} else {
					logger.error("onDataChanged(): create CCAP Failed: {}/{} : {}", thisData.gatePath, thisCcap, message);
				}
				// set the response string in the config ccap object using a new thread
				Response response = new Response(thisData.ccapIID, thisCcap, message);
				executor.execute(response);
			} else {
				// get the PCMM gate parameters from the cmtsId/appId/subId/gateId path in the Maps entry (if new gate)
				for (Map.Entry<InstanceIdentifier<Gates>, Gates> entry : thisData.gateIidMap.entrySet()) {
					Gates gate = entry.getValue();
					InstanceIdentifier<Gates> gateIID = entry.getKey();
					String gateId = gate.getGateId();
					String gatePathStr = thisData.gatePath + "/" + gateId ;
					IpAddress subId = thisData.subId;
					thisCcap = getSubscriberSubnetsLPM(subId);
					message = null;
					if (thisCcap != null) {
						ccapId = thisCcap.getCcapId();
						message = pcmmService.sendGateSet(thisCcap, gatePathStr, subId, gate);
						if (message.contains("200 OK")) {
							gateMap.put(gatePathStr, gate);
							gateCcapMap.put(gatePathStr, thisCcap);
							logger.info("onDataChanged(): created QoS gate {} for {}/{}/{} - {}",
									gateId, ccapId, gatePathStr, gate, message);
						} else {
							logger.error("onDataChanged(): Unable to create QoS gate {} for {}/{}/{} - {}",
									gateId, ccapId, gatePathStr, gate, message);
						}
					} else {
						String subIdStr = thisData.gatePathMap.get("subId");
						message = String.format("404 Not Found - no CCAP found for subscriber %s in %s",
								subIdStr, gatePathStr);
						logger.error("onDataChanged(): create QoS gate {} FAILED: no CCAP found for subscriber {}: @ {}/{}",
								gateId, subIdStr, gatePathStr, gate);
					}
					// set the response message in the config gate object using a new thread
					Response response = new Response(gateIID, gate, message);
					executor.execute(response);
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
				for (Gates gate : thisData.gateIidMap.values()) {
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
