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

	private DataBroker dataBroker;
	private final ExecutorService executor;

	// The following holds the Future for the current make toast task.
	// This is used to cancel the current toast.
	private final AtomicReference<Future<?>> currentConnectionsTasks = new AtomicReference<>();
	private ListenerRegistration<DataChangeListener> listenerRegistration;
	private List<InstanceIdentifier<?>> cmtsInstances = Lists.newArrayList();

	private Map<String, Ccaps> ccapMap = new ConcurrentHashMap<String, Ccaps>();
	private Map<String, Gates> gateMap = new ConcurrentHashMap<String, Gates>();
	private Map<String, Ccaps> gateCcapMap = new ConcurrentHashMap<String, Ccaps>();
	private Map<Subnet, Ccaps> subscriberSubnetsMap = new ConcurrentHashMap<Subnet, Ccaps>();
	private Map<ServiceClassName, List<Ccaps>> downstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private Map<ServiceClassName, List<Ccaps>> upstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private PCMMService pcmmService;


	public PacketcableProvider() {
		executor = Executors.newCachedThreadPool();
		pcmmService = new PCMMService();
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

	private Ccaps findCcapForSubscriberId(IpAddress ipAddress) {
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

	private enum ChangeAction {created, updated, removed};

	@Override
	public void onDataChanged(final AsyncDataChangeEvent<InstanceIdentifier<?>, DataObject> change) {
		Map<InstanceIdentifier<?>, DataObject> createdData = change.getCreatedData();
		Map<InstanceIdentifier<?>, DataObject> updatedData = change.getUpdatedData();
		Map<InstanceIdentifier<?>, DataObject> originalData = change.getOriginalData();
		Set<InstanceIdentifier<?>> removedData = change.getRemovedPaths();

		// Determine what change action took place by looking at the change object's InstanceIdentifier sets
		// and validate all instance data
		ValidateInstanceData validator = null;
		InstanceData thisData = null;
		ChangeAction changeAction = null;
		if (! createdData.isEmpty()) {
			validator = new ValidateInstanceData(dataBroker, createdData);
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
			validator = new ValidateInstanceData(dataBroker, updatedData);
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
					logger.debug("onDataChanged(): created CCAP: {}/{} : {}", thisData.gatePath, thisCcap, message);
				} else {
					logger.error("onDataChanged(): create CCAP Failed: {}/{} : {}", thisData.gatePath, thisCcap, message);
				}
				// set the response string in the config ccap object using a new thread
				Response response = new Response(dataBroker, thisData.ccapIID, thisCcap, message);
				executor.execute(response);
			} else {
				// get the PCMM gate parameters from the cmtsId/appId/subId/gateId path in the Maps entry (if new gate)
				for (Map.Entry<InstanceIdentifier<Gates>, Gates> entry : thisData.gateIidMap.entrySet()) {
					message = null;
					Gates gate = entry.getValue();
					InstanceIdentifier<Gates> gateIID = entry.getKey();
					String gateId = gate.getGateId();
					String gatePathStr = thisData.gatePath + "/" + gateId ;
					IpAddress subId = thisData.subId;
					thisCcap = findCcapForSubscriberId(subId);
					if (thisCcap != null) {
						ccapId = thisCcap.getCcapId();
						// verify SCN exists on CCAP and force gateSpec.Direction to align with SCN direction
						ServiceFlowDirection scnDir = null;
						ServiceClassName scn = gate.getTrafficProfile().getServiceClassName();
						if (scn != null) {
							scnDir = findScnOnCcap(scn, thisCcap);
							if (scnDir != null) {
								message = pcmmService.sendGateSet(thisCcap, gatePathStr, subId, gate, scnDir);
								if (message.contains("200 OK")) {
									gateMap.put(gatePathStr, gate);
									gateCcapMap.put(gatePathStr, thisCcap);
									logger.debug("onDataChanged(): created QoS gate {} for {}/{}/{} - {}",
											gateId, ccapId, gatePathStr, gate, message);
									logger.info("onDataChanged(): created QoS gate {} for {}/{} - {}",
											gateId, ccapId, gatePathStr, message);
								} else {
									logger.debug("onDataChanged(): Unable to create QoS gate {} for {}/{}/{} - {}",
											gateId, ccapId, gatePathStr, gate, message);
									logger.error("onDataChanged(): Unable to create QoS gate {} for {}/{} - {}",
											gateId, ccapId, gatePathStr, message);
								}
							} else {
								logger.error("PCMMService: sendGateSet(): SCN {} not found on CCAP {} for {}/{}",
				                		scn.getValue(), thisCcap, gatePathStr, gate);
								message = String.format("404 Not Found - SCN %s not found on CCAP %s for %s",										scn.getValue(), thisCcap.getCcapId(), gatePathStr);
							}
						}
					} else {
						String subIdStr = thisData.gatePathMap.get("subId");
						message = String.format("404 Not Found - no CCAP found for subscriber %s in %s",
								subIdStr, gatePathStr);
						logger.debug("onDataChanged(): create QoS gate {} FAILED: no CCAP found for subscriber {}: @ {}/{}",
								gateId, subIdStr, gatePathStr, gate);
						logger.error("onDataChanged(): create QoS gate {} FAILED: no CCAP found for subscriber {}: @ {}",
								gateId, subIdStr, gatePathStr);
					}
					// set the response message in the config gate object using a new thread
					Response response = new Response(dataBroker, gateIID, gate, message);
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
				logger.debug("onDataChanged(): updated CCAP " + ccapId + ": FROM: " + lastCcap + " TO: " + thisCcap);
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
					logger.debug("onDataChanged(): updated QoS gate: FROM: " + gatePathStr + "/" + lastGate + " TO: " + gate);
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
					logger.debug("onDataChanged(): removed QoS gate {} for {}/{}/{}: ", gateId, ccapId, gatePathStr, thisGate);
					logger.info("onDataChanged(): removed QoS gate {} for {}/{}: ", gateId, ccapId, gatePathStr);
				}
			}
			for (String ccapIdStr: thisData.removePathList) {
				if (ccapMap.containsKey(ccapIdStr)) {
					thisCcap = ccapMap.remove(ccapIdStr);
					pcmmService.removeCcap(thisCcap);
					logger.debug("onDataChanged(): removed CCAP " + ccapIdStr + "/" + thisCcap);
					logger.info("onDataChanged(): removed CCAP " + ccapIdStr);
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
