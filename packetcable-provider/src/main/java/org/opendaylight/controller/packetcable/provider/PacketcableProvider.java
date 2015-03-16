package org.opendaylight.controller.packetcable.provider;

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
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ConsumerContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.ProviderContext;
import org.opendaylight.controller.sal.binding.api.BindingAwareBroker.RoutedRpcRegistration;
import org.opendaylight.controller.sal.binding.api.BindingAwareProvider;
import org.opendaylight.controller.sal.binding.api.NotificationProviderService;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Uri;
import org.opendaylight.yang.gen.v1.urn.opendaylight.action.types.rev131112.action.list.Action;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.AddFlowOutputBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.RemoveFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.SalFlowService;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.UpdateFlowInput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.UpdateFlowOutput;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.flow.update.OriginalFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.service.rev130819.flow.update.UpdatedFlow;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.transaction.rev131103.TransactionId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.flow.Match;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.instruction.ApplyActionsCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.types.rev131026.instruction.list.Instruction;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeContextRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.NodeRef;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node;
import org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.NodeKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Ccaps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.CcapsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Qos;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.Apps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.AppsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.apps.Subs;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.apps.SubsKey;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.apps.subs.Dsfs;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.flows.apps.subs.DsfsKey;
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
import org.pcmm.gates.IClassifier;
import org.pcmm.gates.ITrafficProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
	private NotificationProviderService notificationProvider;
	private DataBroker dataProvider;

	private final ExecutorService executor;

	// The following holds the Future for the current make toast task.
	// This is used to cancel the current toast.
	private final AtomicReference<Future<?>> currentConnectionsTasks = new AtomicReference<>();
	private ProviderContext providerContext;
	private NotificationProviderService notificationService;
//	private DataBroker dataBroker;
	private ListenerRegistration<DataChangeListener> listenerRegistration;
	private List<InstanceIdentifier<?>> cmtsInstances = Lists.newArrayList();

	private Map<String, Ccaps> ccapMap = Maps.newConcurrentMap();
	private Map<String, Dsfs> flowMap = new ConcurrentHashMap<String, Dsfs>();
	private PCMMDataProcessor pcmmDataProcessor;
	private PcmmService pcmmService;

	public static final InstanceIdentifier<Ccaps>  ccapsIID = InstanceIdentifier.builder(Ccaps.class).build();
	public static final InstanceIdentifier<Qos>  qosIID = InstanceIdentifier.builder(Qos.class).build();

	public PacketcableProvider() {
		executor = Executors.newCachedThreadPool();
		pcmmDataProcessor = new PCMMDataProcessor();
		pcmmService = new PcmmService();
	}

	public void setNotificationProvider(final NotificationProviderService salService) {
		this.notificationProvider = salService;
	}

	public void setDataProvider(final DataBroker salDataProvider) {
		this.dataProvider = salDataProvider;
	}

	/**
	 * Implemented from the AutoCloseable interface.
	 */
	@Override
	public void close() throws ExecutionException, InterruptedException {
		executor.shutdown();
		if (dataProvider != null) {
			for (Iterator<InstanceIdentifier<?>> iter = cmtsInstances.iterator(); iter.hasNext();) {
				WriteTransaction tx = dataProvider.newWriteOnlyTransaction();
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

	private class PcmmService {
		private Map<IpAddress, IPSCMTSClient> ccapClients;
		private IPCMMPolicyServer policyServer;

		public PcmmService() {
			policyServer = new PCMMPolicyServer();
			ccapClients = Maps.newConcurrentMap();
		}

		public void addCcap(Ccaps ccap) {
			IpAddress ipAddr = ccap.getNetwork().getIpAddress();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("addCcap(): " + ipv4);
			IPSCMTSClient client = policyServer.requestCMTSConnection(ipv4);
			if (client.isConnected()) {
				ccapClients.put(ipAddr, client);
				logger.info("addCcap(): connected:" + ipv4);
			}
		}

		public void removeCcap(Ccaps ccap) {
			IpAddress ipAddr = ccap.getNetwork().getIpAddress();
			String ipv4 = ipAddr.getIpv4Address().getValue();
			logger.info("removeCcap(): " + ipv4);
			if (ccapClients.containsKey(ipAddr)) {
				IPSCMTSClient client = ccapClients.remove(ipAddr);
				client.disconnect();
				logger.info("removeCcap(): disconnected: " + ipv4);
			}
		}

		public Boolean sendGateSet() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = ccapClients.values().iterator(); iter.hasNext();)
				ret &= ccapClients.get(0).gateSet();
			return ret;
		}

		public Boolean sendGateDelete() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = ccapClients.values().iterator(); iter.hasNext();)
				ret &= ccapClients.get(0).gateDelete();
			return ret;
		}

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

	}


	/**
	 * Implemented from the DataChangeListener interface.
	 */

	private class InstanceData {
		public Map<String, String> flowPathMap = new HashMap<String, String>();
		public String flowPath;
		public String ccapId;
		public Ccaps ccap;
		public List<Dsfs> flowList = new ArrayList<Dsfs>();
		public Set<String> removePathList = new HashSet<String>();

		public InstanceData(Map<InstanceIdentifier<?>, DataObject> thisData) {
			// only used to parse createdData or updatedData
			getCcap(thisData);
			if (ccap != null) {
				ccapId = ccap.getCcapId();
				flowPath = ccapId;
			} else {
				getFlows(thisData);
				if (! flowList.isEmpty()){
					flowPath = flowPathMap.get("appId") + "/" + flowPathMap.get("subId");
				}
			}
		}

		public InstanceData(Set<InstanceIdentifier<?>> thisData) {
			// only used to parse the removedData paths
			for (InstanceIdentifier<?> removeThis : thisData) {
				getFlowPathMap(removeThis);
				if (flowPathMap.containsKey("ccapId")) {
					flowPath = flowPathMap.get("ccapId");
					removePathList.add(flowPath);
				} else if (flowPathMap.containsKey("flowId")) {
					flowPath = flowPathMap.get("appId") + "/" + flowPathMap.get("subId") + "/" + flowPathMap.get("flowId");
					removePathList.add(flowPath);
				}
			}
		}
		private void getFlowPathMap(InstanceIdentifier<?> thisInstance) {
			logger.info("onDataChanged().getFlowPathMap(): " + thisInstance);
			try {
				InstanceIdentifier<Ccaps> ccapInstance = thisInstance.firstIdentifierOf(Ccaps.class);
				if (ccapInstance != null) {
					CcapsKey ccapKey = InstanceIdentifier.keyOf(ccapInstance);
					if (ccapKey != null) {
						String ccapId = ccapKey.getCcapId();
						flowPathMap.put("ccapId", ccapId);
					}
				} else {
					// get the flow path keys from the InstanceIdentifier Map key set if they are there
					InstanceIdentifier<Apps> appsInstance = thisInstance.firstIdentifierOf(Apps.class);
					if (appsInstance != null) {
						AppsKey appKey = InstanceIdentifier.keyOf(appsInstance);
						if (appKey != null) {
							String appId = appKey.getAppId();
							flowPathMap.put("appId", appId);
						}
					}
					InstanceIdentifier<Subs> subsInstance = thisInstance.firstIdentifierOf(Subs.class);
					if (subsInstance != null) {
						SubsKey subKey = InstanceIdentifier.keyOf(subsInstance);
						if (subKey != null) {
							String subId = getIpAddressStr(subKey.getSubId());
							flowPathMap.put("subId", subId);
						}
					}
					InstanceIdentifier<Dsfs> flowsInstance = thisInstance.firstIdentifierOf(Dsfs.class);
					if (flowsInstance != null) {
						DsfsKey flowKey = InstanceIdentifier.keyOf(flowsInstance);
						if (flowKey != null) {
							String flowId = flowKey.getDsfId();
							flowPathMap.put("flowId", flowId);
						}
					}
				}
			} catch (ClassCastException err) {
				// do nothing, failure is ok
			}
		}

		private void getCcap(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.info("onDataChanged().getCcap(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Ccaps) {
		            ccap = (Ccaps)entry.getValue();
		        }
		    }
		}

		private void getFlows(Map<InstanceIdentifier<?>, DataObject> thisData) {
			logger.info("onDataChanged().getFlows(): " + thisData);
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Dsfs) {
					Dsfs flow = (Dsfs)entry.getValue();
					InstanceIdentifier<?> iid = entry.getKey();
					getFlowPathMap(iid);
					flowList.add(flow);
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
			logger.info("onDataChanged().createdData: " + createdData);
			logger.info("onDataChanged().updatedData: " + updatedData);
			logger.info("onDataChanged().originalData: " + originalData);
			logger.info("onDataChanged().removedData: " + removedData);
			logger.info("onDataChanged().originalSubtree: " + originalSubtree);
			logger.info("onDataChanged().updatedSubtree: " + updatedSubtree);
		} catch (IllegalArgumentException | IllegalStateException err) {
			logger.warn("onDataChanged().change: Illegal Element Value: " + err);
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
		Dsfs lastFlow = null;
		Dsfs thisFlow = null;
		switch (changeAction) {
		case created:
			// get the CMTS parameters from the CmtsNode in the Map entry (if new CMTS)
			thisCcap = thisData.ccap;
			if (thisCcap != null) {
				// get the CMTS node identity from the Instance Data
				ccapId = thisData.ccapId;
				logger.info("onDataChanged(): created CCAP: " + thisData.flowPath + "/" + thisCcap);
				ccapMap.put(ccapId, thisCcap);
				pcmmService.addCcap(thisCcap);
			}
			// get the PCMM flow parameters from the cmtsId/appId/subId/flowId path in the Maps entry (if new flow)
			for (Dsfs flow : thisData.flowList) {
				String flowId = flow.getDsfId();
				String flowPathStr = thisData.flowPath + "/" + flowId ;
				flowMap.put(flowPathStr, flow);
				logger.info("onDataChanged(): created serviceFlow: " + flowId + " @ " + flowPathStr + "/" + flow);
			}
			break;
		case updated:
			thisCcap = thisData.ccap;
			if (thisCcap != null) {
				// get the CMTS node identity from the Instance Data
				ccapId = thisData.ccapId;
				lastCcap = ccapMap.get(ccapId);
				logger.info("onDataChanged(): updated " + ccapId + ": FROM: " + lastCcap + " TO: " + thisCcap);
				ccapMap.put(ccapId, thisCcap);
				// remove original cmtsNode
				pcmmService.removeCcap(lastCcap);
				// and add back the new one
				pcmmService.addCcap(thisCcap);
			}
			for (Dsfs flow : thisData.flowList) {
				String flowId = flow.getDsfId();
				String flowPathStr = thisData.flowPath + "/" + flowId ;
				lastFlow = flowMap.get(flowPathStr);
				logger.info("onDataChanged(): updated Flow: FROM: " + flowPathStr + "/" + lastFlow + " TO: " + flow);
			}
			break;
		case removed:
			// remove flows before removing CMTS
			for (String flowPathStr: thisData.removePathList) {
				if (flowMap.containsKey(flowPathStr)) {
					lastFlow = flowMap.remove(flowPathStr);
					logger.info("onDataChanged(): removed Flow: " + flowPathStr + "/" + lastFlow);
				}
			}
			for (String ccapIdStr: thisData.removePathList) {
				if (ccapMap.containsKey(ccapIdStr)) {
					thisCcap = ccapMap.remove(ccapIdStr);
					logger.info("onDataChanged(): removed " + ccapIdStr + "/" + thisCcap);
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

//	@Override
//	public Collection<? extends RpcService> getImplementations() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public Collection<? extends ProviderFunctionality> getFunctionality() {
//		// TODO Auto-generated method stub
//		return null;
//	}
//
//	@Override
//	public void onSessionInitiated(ProviderContext session) {
//		// TODO Auto-generated method stub
//
//	}
//
//	@Override
//	public void onSessionInitialized(ConsumerContext session) {
//		// TODO Auto-generated method stub
//
//	}
//

/* #####
	public void notifyConsumerOnCmtsAdd(CmtsNode input, TransactionId transactionId) {
		CmtsAdded cmtsAdded = new CmtsAddedBuilder().setAddress(input.getAddress()).setPort(input.getPort()).setTransactionId(transactionId).build();
		notificationService.publish(cmtsAdded);
	}

	public void notifyConsumerOnCmtsRemove(CmtsNode input, TransactionId transactionId) {
		CmtsRemoved cmtsRemoved = new CmtsRemovedBuilder().setAddress(input.getAddress()).setPort(input.getPort()).setTransactionId(transactionId).build();
		notificationService.publish(cmtsRemoved);
	}

	public void notifyConsumerOnCmtsUpdate(CmtsNode input, TransactionId transactionId) {
		// Obsolete method: do not use
//		CmtsUpdated cmtsUpdated = new CmtsUpdatedBuilder().setAddress(input.getAddress()).setPort(input.getPort()).setTransactionId(transactionId).build();
//		notificationService.publish(cmtsUpdated);
	}

	@Override
	public Future<RpcResult<AddFlowOutput>> addFlow(AddFlowInput input) {
		Match match = input.getMatch();
		CmtsNode cmts = getCmtsNode(input);
		if (cmts != null)
			cmtsInstances.add(input.getNode().getValue());
		IClassifier classifier = buildClassifier(match);
		ITrafficProfile trafficProfie = null;
		for (Instruction i : input.getInstructions().getInstruction()) {
			if (i.getInstruction() instanceof ApplyActionsCase) {
				ApplyActionsCase aac = (ApplyActionsCase) i.getInstruction();
				for (Action a : aac.getApplyActions().getAction()) {
					if (a.getAction() instanceof FlowspecCase) {
						// not implemented
						// trafficProfie = buildTrafficProfile(((FlowspecCase)
						// a.getAction()).getFlowspec());
					} else if (a.getAction() instanceof BestEffortCase) {
						trafficProfie = buildTrafficProfile(((BestEffortCase) a.getAction()).getBestEffort());
						break;
					} else if (a.getAction() instanceof DocsisServiceClassNameCase) {
						trafficProfie = buildTrafficProfile(((DocsisServiceClassNameCase) a.getAction()).getDocsisServiceClassName());
						break;
					}
				}
			}
		}
		TransactionId transactionId = null;
		notifyConsumerOnCmtsAdd(cmts, transactionId);
		return Futures.immediateFuture(RpcResultBuilder.success(new AddFlowOutputBuilder().setTransactionId(transactionId).build()).build());
	}

	@Override
	public ITrafficProfile buildTrafficProfile(TrafficProfileDocsisServiceClassNameAttributes docsis) {
		return pcmmDataProcessor.process(docsis);
	}

	@Override
	public ITrafficProfile buildTrafficProfile(TrafficProfileBestEffortAttributes bestEffort) {
		return pcmmDataProcessor.process(bestEffort);
	}

	@Override
	public ITrafficProfile buildTrafficProfile(TrafficProfileFlowspecAttributes flowSpec) {
		return pcmmDataProcessor.process(flowSpec);
	}

	@Override
	public IClassifier buildClassifier(Match match) {
		return pcmmDataProcessor.process(match);
	}

	@Override
	public Future<RpcResult<RemoveFlowOutput>> removeFlow(RemoveFlowInput input) {
		UdpMatchRangesRpcRemoveFlow updRange = input.getMatch().getAugmentation(UdpMatchRangesRpcRemoveFlow.class);
		notifyConsumerOnCmtsRemove(getCmtsNode(input), null);
		return null;
	}

	@Override
	public Future<RpcResult<UpdateFlowOutput>> updateFlow(UpdateFlowInput input) {
		OriginalFlow foo = input.getOriginalFlow();
		UdpMatchRangesRpcUpdateFlowOriginal bar = foo.getMatch().getAugmentation(UdpMatchRangesRpcUpdateFlowOriginal.class);
		UpdatedFlow updated = input.getUpdatedFlow();
		UdpMatchRangesRpcUpdateFlowUpdated updatedRange = updated.getMatch().getAugmentation(UdpMatchRangesRpcUpdateFlowUpdated.class);
		notifyConsumerOnCmtsUpdate(getCmtsNode(input), null);
		return null;
	}

	@SuppressWarnings("unchecked")
	protected CmtsNode getCmtsNode(NodeContextRef input) {
		NodeRef nodeRef = input.getNode();
		InstanceIdentifier<Node> instanceIdentifier = (InstanceIdentifier<Node>) nodeRef.getValue();
		ReadOnlyTransaction rtransaction = dataBroker.newReadOnlyTransaction();
		CheckedFuture<Optional<Node>, ReadFailedException> value = rtransaction.read(LogicalDatastoreType.CONFIGURATION, instanceIdentifier);
		rtransaction.close();
		Optional<Node> opt = null;
		try {
			opt = value.get();
		} catch (Exception e) {
			logger.error(e.getMessage());
			return null;
		}
		Node node = opt.get();
		CmtsCapableNode cmts = node.getAugmentation(CmtsCapableNode.class);
		CmtsNode cmtsNode = cmts.getCmtsNode();
		return cmtsNode;
	}

	@Override
	public Collection<? extends RpcService> getImplementations() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Collection<? extends ProviderFunctionality> getFunctionality() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void onSessionInitiated(ProviderContext session) {
		providerContext = session;
		notificationService = session.getSALService(NotificationProviderService.class);
		dataBroker = session.getSALService(DataBroker.class);
		InstanceIdentifier<CmtsNode> listenTo = InstanceIdentifier.create(Nodes.class).child(Node.class).augmentation(CmtsCapableNode.class).child(CmtsNode.class);
		listenerRegistration = dataBroker.registerDataChangeListener(LogicalDatastoreType.CONFIGURATION, listenTo, this, DataChangeScope.BASE);
	}

	@Override
	public void onSessionInitialized(ConsumerContext session) {
		// Noop

	}
*/

//	public void onSessionAdded(/* Whatever you need per CmtsConnection */) {
//		CompositeObjectRegistrationBuilder<OpendaylightPacketcableProvider> builder = CompositeObjectRegistration.<OpendaylightPacketcableProvider> builderFor(this);
//		/*
//		 * You will need a routedRpc registration per Cmts... I'm not doing the
//		 * accounting of storing them here, but you will need to so you can
//		 * close them when your provider is closed
//		 */
//		RoutedRpcRegistration<SalFlowService> registration = providerContext.addRoutedRpcImplementation(SalFlowService.class, this);
//		/*
//		 * You will need to get your identifier somewhere... this is your
//		 * nodeId. I would recommend adoption a convention like
//		 * "cmts:<ipaddress>" for CmtsCapableNodes
//		 * registration.registerPath(NodeContext.class, getIdentifier());
//		 */
//	}
}