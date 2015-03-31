package org.opendaylight.controller.packetcable.provider;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.opendaylight.controller.md.sal.binding.api.DataBroker;
import org.opendaylight.controller.md.sal.binding.api.DataChangeListener;
import org.opendaylight.controller.md.sal.common.api.data.AsyncDataChangeEvent;
import org.opendaylight.controller.md.sal.common.api.data.AsyncReadWriteTransaction;
import org.opendaylight.controller.md.sal.common.api.data.LogicalDatastoreType;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Prefix;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.ccap.Ccaps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.ccap.CcapsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.Ccap;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.Qos;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.ServiceClassName;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.ServiceFlowDirection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.AppsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.apps.Subs;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.apps.SubsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.apps.subs.Gates;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150327.pcmm.qos.gates.apps.subs.GatesKey;
import org.opendaylight.yangtools.yang.binding.DataObject;
import org.opendaylight.yangtools.yang.binding.InstanceIdentifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;



//@SuppressWarnings("unused")
public class PacketcableProvider implements DataChangeListener, AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(PacketcableProvider.class);

	// keys to the /restconf/config/packetcable:ccap and /restconf/config/packetcable:qos config datastore
	public static final InstanceIdentifier<Ccap> ccapIID = InstanceIdentifier.builder(Ccap.class).build();
	public static final InstanceIdentifier<Qos> qosIID = InstanceIdentifier.builder(Qos.class).build();

	private DataBroker dataBroker;
	private final ExecutorService executor;

	private Map<String, Ccaps> ccapMap = new ConcurrentHashMap<String, Ccaps>();
	private Map<String, Gates> gateMap = new ConcurrentHashMap<String, Gates>();
	private Map<String, String> gateCcapMap = new ConcurrentHashMap<String, String>();
	private Map<Subnet, Ccaps> subscriberSubnetsMap = new ConcurrentHashMap<Subnet, Ccaps>();
	private Map<ServiceClassName, List<Ccaps>> downstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private Map<ServiceClassName, List<Ccaps>> upstreamScnMap = new ConcurrentHashMap<ServiceClassName, List<Ccaps>>();
	private PCMMService pcmmService;


	public PacketcableProvider() {
		executor = Executors.newCachedThreadPool();
		pcmmService = new PCMMService();
	}

	public void setDataBroker(final DataBroker salDataBroker) {
		this.dataBroker = salDataBroker;
	}

	/**
	 * Implemented from the AutoCloseable interface.
	 */
	@SuppressWarnings("deprecation")
	@Override
	public void close() throws ExecutionException, InterruptedException {
		executor.shutdown();
        if (dataBroker != null) {
        	// remove our config datastore instances
            final AsyncReadWriteTransaction<InstanceIdentifier<?>, ?> tx = dataBroker.newReadWriteTransaction();
            tx.delete(LogicalDatastoreType.CONFIGURATION, ccapIID);
            tx.delete(LogicalDatastoreType.CONFIGURATION, qosIID);
            tx.commit().get();
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

	private void removeCcapFromAllMaps(Ccaps ccap) {
		// remove the ccap from all maps
		// subscriberSubnets map
		for (Map.Entry<Subnet, Ccaps> entry : subscriberSubnetsMap.entrySet()) {
			if (entry.getValue() == ccap) {
				subscriberSubnetsMap.remove(entry.getKey());
			}
		}
		// ccap to upstream SCN map
		for (Map.Entry<ServiceClassName, List<Ccaps>> entry : upstreamScnMap.entrySet()) {
			List<Ccaps> ccapList = entry.getValue();
			ccapList.remove(ccap);
			if (ccapList.isEmpty()) {
				upstreamScnMap.remove(entry.getKey());
			}
		}
		// ccap to downstream SCN map
		for (Map.Entry<ServiceClassName, List<Ccaps>> entry : downstreamScnMap.entrySet()) {
			List<Ccaps> ccapList = entry.getValue();
			ccapList.remove(ccap);
			if (ccapList.isEmpty()) {
				downstreamScnMap.remove(entry.getKey());
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
		public Map<InstanceIdentifier<Ccaps>, Ccaps> ccapIidMap = new HashMap<InstanceIdentifier<Ccaps>, Ccaps>();
		// Gate Identity
		public IpAddress subId;
		public Map<String, String> gatePathMap = new HashMap<String, String>();
		public String gatePath;
		public Map<InstanceIdentifier<Gates>, Gates> gateIidMap = new HashMap<InstanceIdentifier<Gates>, Gates>();
		// remove path for either CCAP or Gates
		public Set<String> removePathList = new HashSet<String>();

		public InstanceData(Map<InstanceIdentifier<?>, DataObject> thisData) {
			// only used to parse createdData or updatedData
			getCcaps(thisData);
			if (ccapIidMap.isEmpty()) {
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

		@SuppressWarnings("unchecked")
		private void getCcaps(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.debug("onDataChanged().getCcaps(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Ccaps) {
					Ccaps ccap = (Ccaps)entry.getValue();
					InstanceIdentifier<Ccaps> ccapIID = (InstanceIdentifier<Ccaps>)entry.getKey();
					ccapIidMap.put(ccapIID, ccap);
				}
		    }
		}

		@SuppressWarnings("unchecked")
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
		InstanceData oldData = null;
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
			oldData = new InstanceData(originalData);
			logger.debug("onDataChanged().originalData: " + originalData);
		} else {
			// we should not be here -- complain bitterly and return
			logger.error("onDataChanged(): Unknown change action: " + change);
			return;
		}

		// select the change action
		String ccapId = null;
		Ccaps thisCcap = null;
		Gates thisGate = null;
		switch (changeAction) {
		case created:
			// get the CCAP parameters
			String message = null;
			if (! thisData.ccapIidMap.isEmpty()) {
				for (Map.Entry<InstanceIdentifier<Ccaps>, Ccaps> entry : thisData.ccapIidMap.entrySet()) {
					InstanceIdentifier<Ccaps> thisCcapIID = entry.getKey();
					thisCcap = entry.getValue();
					// get the CCAP node identity from the Instance Data
					ccapId = thisCcap.getCcapId();
					message = pcmmService.addCcap(thisCcap);
					if (message.contains("200 OK")) {
						ccapMap.put(ccapId, thisCcap);
						updateCcapMaps(thisCcap);
						logger.debug("onDataChanged(): created CCAP: {}/{} : {}", thisData.gatePath, thisCcap, message);
						logger.info("onDataChanged(): created CCAP: {} : {}", thisData.gatePath, message);
					} else {
						logger.error("onDataChanged(): create CCAP Failed: {} : {}", thisData.gatePath, message);
					}
					// set the response string in the config ccap object using a new thread
					Response response = new Response(dataBroker, thisCcapIID, thisCcap, message);
					executor.execute(response);
				}
			} else {
				// get the PCMM gate parameters from the ccapId/appId/subId/gateId path in the Maps entry (if new gate)
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
									gateCcapMap.put(gatePathStr, thisCcap.getCcapId());
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
			// update operation not allowed -- restore the original config object and complain
			if (! oldData.ccapIidMap.isEmpty()) {
				for (Map.Entry<InstanceIdentifier<Ccaps>, Ccaps> entry : oldData.ccapIidMap.entrySet()) {
					InstanceIdentifier<Ccaps> ccapIID = entry.getKey();
					Ccaps ccap = entry.getValue();
					ccapId = ccap.getCcapId();
					message = String.format("405 Method Not Allowed - %s: CCAP update not permitted (use delete); ", ccapId);
					// push new error message onto existing response
					message += ccap.getResponse();
					// set the response message in the config object using a new thread -- also restores the original data
					Response response = new Response(dataBroker, ccapIID, ccap, message);
					executor.execute(response);
					logger.error("onDataChanged(): CCAP update not permitted {}/{}", ccapId, ccap);
				}
			} else {
				for (Map.Entry<InstanceIdentifier<Gates>, Gates> entry : oldData.gateIidMap.entrySet()) {
					InstanceIdentifier<Gates> gateIID = entry.getKey();
					Gates gate = entry.getValue();
					String gateId = gate.getGateId();
					String gatePathStr = oldData.gatePath + "/" + gateId ;
					message = String.format("405 Method Not Allowed - %s: QoS Gate update not permitted (use delete); ", gatePathStr);
					// push new error message onto existing response
					message += gate.getResponse();
					// set the response message in the config object using a new thread -- also restores the original data
					Response response = new Response(dataBroker, gateIID, gate, message);
					executor.execute(response);
					logger.error("onDataChanged(): QoS Gate update not permitted: {}/{}", gatePathStr, gate);
				}
			}
			break;
		case removed:
			// remove gates before removing CMTS
			for (String gatePathStr: thisData.removePathList) {
				if (gateMap.containsKey(gatePathStr)) {
					thisGate = gateMap.remove(gatePathStr);
					String gateId = thisGate.getGateId();
					ccapId = gateCcapMap.remove(gatePathStr);
					thisCcap = ccapMap.get(ccapId);
					pcmmService.sendGateDelete(thisCcap, gatePathStr);
					logger.debug("onDataChanged(): removed QoS gate {} for {}/{}/{}: ", gateId, ccapId, gatePathStr, thisGate);
					logger.info("onDataChanged(): removed QoS gate {} for {}/{}: ", gateId, ccapId, gatePathStr);
				}
			}
			for (String ccapIdStr: thisData.removePathList) {
				if (ccapMap.containsKey(ccapIdStr)) {
					thisCcap = ccapMap.remove(ccapIdStr);
					removeCcapFromAllMaps(thisCcap);
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
