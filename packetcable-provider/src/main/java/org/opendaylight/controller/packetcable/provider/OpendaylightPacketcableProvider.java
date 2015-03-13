package org.opendaylight.controller.packetcable.provider;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.TrafficProfileBestEffortAttributes;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.TrafficProfileDocsisServiceClassNameAttributes;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.TrafficProfileFlowspecAttributes;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.add.flow.input.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.BestEffortCase;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.add.flow.input.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.DocsisServiceClassNameCase;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.flow.traffic.profile.rev140908.add.flow.input.instructions.instruction.instruction.apply.actions._case.apply.actions.action.action.FlowspecCase;
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
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsAdded;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsAddedBuilder;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsRemoved;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsRemovedBuilder;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsUpdated;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.broker.rev140909.CmtsUpdatedBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.CmtsCapableNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.DocsisServiceClassName;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.PcmmFlows;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.nodes.node.CmtsNode;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.Apps;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.apps.Subs;
import org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.apps.subs.Flows;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.packetcable.match.types.rev140909.UdpMatchRangesRpcRemoveFlow;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.packetcable.match.types.rev140909.UdpMatchRangesRpcUpdateFlowOriginal;
//import org.opendaylight.yang.gen.v1.urn.opendaylight.packetcable.match.types.rev140909.UdpMatchRangesRpcUpdateFlowUpdated;
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
public class OpendaylightPacketcableProvider implements DataChangeListener,
		//SalFlowService,
		OpenDaylightPacketCableProviderService,
		BindingAwareProvider,
		AutoCloseable {

	private static final Logger logger = LoggerFactory.getLogger(OpendaylightPacketcableProvider.class);
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

	private Map<String, CmtsNode> cmtsNodeMap = Maps.newConcurrentMap();
	private Map<String, Flows> flowMap = new ConcurrentHashMap<String, Flows>();
	private PCMMDataProcessor pcmmDataProcessor;
	private PcmmService pcmmService;

	public static final InstanceIdentifier<Node>  nodeIID =
            InstanceIdentifier.builder(Nodes.class).child(Node.class).build();

	public OpendaylightPacketcableProvider() {
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
		private Map<IpAddress, IPSCMTSClient> cmtsClients;
		private IPCMMPolicyServer policyServer;

		public PcmmService() {
			policyServer = new PCMMPolicyServer();
			cmtsClients = Maps.newConcurrentMap();
		}

		public void addCmts(CmtsNode cmtsNode) {
			String ipv4 = cmtsNode.getAddress().getIpv4Address().getValue();
			logger.info("addCmts(): " + ipv4);
			IPSCMTSClient client = policyServer.requestCMTSConnection(ipv4);
			if (client.isConnected()) {
				cmtsClients.put(cmtsNode.getAddress(), client);
				logger.info("addCmts(): connected:" + ipv4);
			}
		}

		public void removeCmts(CmtsNode cmtsNode) {
			String ipv4 = cmtsNode.getAddress().getIpv4Address().getValue();
			logger.info("removeCmts(): " + ipv4);
			if (cmtsClients.containsKey(cmtsNode.getAddress())) {
				IPSCMTSClient client = cmtsClients.remove(cmtsNode.getAddress());
				client.disconnect();
				logger.info("removeCmts(): disconnected: " + ipv4);
			}
		}

		public Boolean sendGateSet() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = cmtsClients.values().iterator(); iter.hasNext();)
				ret &= cmtsClients.get(0).gateSet();
			return ret;
		}

		public Boolean sendGateDelete() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = cmtsClients.values().iterator(); iter.hasNext();)
				ret &= cmtsClients.get(0).gateDelete();
			return ret;
		}

		public Boolean sendGateSynchronize() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = cmtsClients.values().iterator(); iter.hasNext();)
				ret &= cmtsClients.get(0).gateSynchronize();
			return ret;
		}

		public Boolean sendGateInfo() {
			// TODO change me
			boolean ret = true;
			for (Iterator<IPSCMTSClient> iter = cmtsClients.values().iterator(); iter.hasNext();)
				ret &= cmtsClients.get(0).gateInfo();
			return ret;
		}

	}


	/**
	 * Implemented from the DataChangeListener interface.
	 */

	private class InstanceData {
		public Map<String, String> flowPathMap = new HashMap<String, String>();
		public String flowPath;
		public String cmtsId;
		public CmtsNode cmtsNode;
		public List<Flows> flowList = new ArrayList<Flows>();

		public InstanceData(Map<InstanceIdentifier<?>, DataObject> thisData) {
			getFlowPathMap(thisData);
			getCmtsNode(thisData);
			if (cmtsNode != null) {
				cmtsId = flowPathMap.get("cmtsId");
				flowPath = cmtsId;
			}
			getFlows(thisData);
			if (! flowList.isEmpty()){
				flowPath = flowPathMap.get("cmtsId") + "/" + flowPathMap.get("appId") + "/" + flowPathMap.get("subId");
			}
		}

		private void getFlowPathMap(Map<InstanceIdentifier<?>, DataObject> thisData) {
			// get the flow path keys from the InstanceIdentifier Map key set if they are there
			InstanceIdentifier<?> nodesInstance = thisData.keySet().iterator().next();

			InstanceIdentifier<Node> cmtsNodeInstance = nodesInstance.firstIdentifierOf(Node.class);
			if (cmtsNodeInstance != null) {
				String cmtsId = InstanceIdentifier.keyOf(cmtsNodeInstance).getId().getValue();
				flowPathMap.put("cmtsId", cmtsId);
			}
			InstanceIdentifier<Apps> appsInstance = nodesInstance.firstIdentifierOf(Apps.class);
			if (appsInstance != null) {
				String appId = InstanceIdentifier.keyOf(appsInstance).getAppId();
				flowPathMap.put("appId", appId);
			}
			InstanceIdentifier<Subs> subsInstance = nodesInstance.firstIdentifierOf(Subs.class);
			if (subsInstance != null) {
				String subId = getIpAddressStr(InstanceIdentifier.keyOf(subsInstance).getSubId());
				flowPathMap.put("subId", subId);
			}
		}

		private void getCmtsNode(Map<InstanceIdentifier<?>, DataObject> thisData) {
			//	{InstanceIdentifier{
			//		targetType=interface org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.nodes.node.CmtsNode,
			//		path=[org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes,
			//				org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node[
			//					key=NodeKey [_id=Uri [_value=cmts:192.168.1.2]]],
			//				org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.CmtsCapableNode,
			//				org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.nodes.node.CmtsNode]
			//	}=CmtsNode{
			//		getAddress=IpAddress [_ipv4Address=Ipv4Address [_value=192.168.1.2], _value=[1, 9, 2, ., 1, 6, 8, ., 1, ., 2]],
			//		getPort=PortNumber [_value=3918],
			//		augmentations={}
			//	}}
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof CmtsNode) {
		            cmtsNode = (CmtsNode)entry.getValue();
		        }
		    }
		}

		private void getFlows(Map<InstanceIdentifier<?>, DataObject> thisData) {
			//{KeyedInstanceIdentifier{
			//	targetType=interface org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.apps.subs.Flows,
			//	path=[org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes,
			//	org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node[
			//		key=NodeKey [_id=Uri [_value=cmts:192.168.1.2]]],
			//	org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.PcmmFlows,
			//	org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.Apps[
			//		key=AppsKey [_appId=testApp]],
			//	org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.apps.Subs[
			//		key=SubsKey [_subId=IpAddress [_ipv4Address=Ipv4Address [_value=44.137.1.20], _value=[4, 4, ., 1, 3, 7, ., 1, ., 2, 0]]]],
			//	org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.pcmm.apps.apps.subs.Flows[
			//		key=FlowsKey [_flowId=ipvideo_dn-2]]]
			//	}=Flows{
			//		getFlowId=ipvideo_dn-2, getServiceClassName=ipvideo_dn, augmentations={}}}

			Flows flow = null;
			for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
				if (entry.getValue() instanceof Flows) {
		            flow = (Flows)entry.getValue();
		            flowList.add(flow);
		        }
		    }
			if (flow == null) {
				// {InstanceIdentifier{
				//		targetType=interface org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.PcmmFlows,
				//		path=[org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.Nodes,
				//		org.opendaylight.yang.gen.v1.urn.opendaylight.inventory.rev130819.nodes.Node[
				//			key=NodeKey [_id=Uri [_value=cmts:192.168.1.2]]],
				//		org.opendaylight.yang.gen.v1.urn.opendaylight.node.cmts.rev140909.PcmmFlows]
				//	}=PcmmFlows{
				//		getApps=[Apps{getAppId=testApp,
				//		getSubs=[Subs{getSubId=IpAddress [_ipv4Address=Ipv4Address [_value=44.137.1.20], _value=[4, 4, ., 1, 3, 7, ., 1, ., 2, 0]],
				//		getFlows=[Flows{getFlowId=ipvideo_dn-1, getServiceClassName=ipvideo_dn, augmentations={}}],
				//		augmentations={}}], augmentations={}}]},
				for (Map.Entry<InstanceIdentifier<?>, DataObject> entry : thisData.entrySet()) {
					if (entry.getValue() instanceof PcmmFlows) {
						PcmmFlows pcmmFlow = (PcmmFlows)entry.getValue();
						Apps app = pcmmFlow.getApps().iterator().next();
						String appId = app.getAppId();
						flowPathMap.put("appID", appId);
						Subs sub = app.getSubs().iterator().next();
						String subId = getIpAddressStr(sub.getSubId());
						flowPathMap.put("subId", subId);
						flowList.addAll(sub.getFlows());
			        }
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
		ChangeAction changeAction = null;
		Map<InstanceIdentifier<?>, DataObject> createdData = change.getCreatedData();
		Map<InstanceIdentifier<?>, DataObject> updatedData = change.getUpdatedData();
		Map<InstanceIdentifier<?>, DataObject> originalData = change.getOriginalData();
		Set<InstanceIdentifier<?>> removedData = change.getRemovedPaths();
		InstanceData thisData = null;

		// Determine what change action took place by looking at the change object's InstanceIdentifier sets
		if (! createdData.isEmpty()) {
			changeAction = ChangeAction.created;
			thisData = new InstanceData(createdData);
		} else if (! removedData.isEmpty()) {
			changeAction = ChangeAction.removed;
			thisData = new InstanceData(originalData);
		} else if (! updatedData.isEmpty()) {
				changeAction = ChangeAction.updated;
				thisData = new InstanceData(updatedData);
		} else {
				// we should not be here -- complain bitterly and return
				logger.error("onDataChanged(): Unknown change action: " + change);
				return;
		}

		// get the CMTS node identity from the Instance Data
		String cmtsId = thisData.cmtsId;


		// select the change action
		CmtsNode lastCmtsNode = null;
		CmtsNode thisCmtsNode = null;
		Flows lastFlow = null;
		Flows thisFlow = null;
		switch (changeAction) {
		case created:
			// get the CMTS parameters from the CmtsNode in the Map entry (if new CMTS)
			thisCmtsNode = thisData.cmtsNode;
			if (thisCmtsNode != null) {
				logger.info("onDataChanged(): created CMTS Node " + thisData.flowPath + "/" + thisCmtsNode);
				cmtsNodeMap.put(cmtsId, thisCmtsNode);
				pcmmService.addCmts(thisCmtsNode);
			}
			// get the PCMM flow parameters from the cmtsId/appId/subId/flowId path in the Maps entry (if new flow)
			for (Flows flow : thisData.flowList) {
				String flowId = flow.getFlowId();
				String scn = flow.getServiceClassName();
				String flowPathStr = thisData.flowPath + "/" + flowId ;
				flowMap.put(flowPathStr, flow);
				logger.info("onDataChanged(): created Flow: " + flowId + " @ " + flowPathStr + "/" + flow);
			}
			break;
		case updated:
			thisCmtsNode = thisData.cmtsNode;
			if (thisCmtsNode != null) {
				lastCmtsNode = cmtsNodeMap.get(cmtsId);
				logger.info("onDataChanged(): updated " + cmtsId + ": FROM: " + lastCmtsNode + " TO: " + thisCmtsNode);
				cmtsNodeMap.put(cmtsId, thisCmtsNode);
				// remove original cmtsNode
				pcmmService.removeCmts(lastCmtsNode);
				// and add back the new one
				pcmmService.addCmts(thisCmtsNode);
			}
			for (Flows flow : thisData.flowList) {
				String flowId = flow.getFlowId();
				String scn = flow.getServiceClassName();
				String flowPathStr = thisData.flowPath + "/" + flowId ;
				lastFlow = flowMap.get(flowPathStr);
				logger.info("onDataChanged(): updated Flow: FROM: " + flowPathStr + "/" + lastFlow + " TO: " + flow);
			}
			break;
		case removed:
			// remove flows before removing CMTS
			for (Flows flow : thisData.flowList) {
				String flowId = flow.getFlowId();
				String flowPathStr = thisData.flowPath + "/" + flowId ;
				lastFlow = flowMap.remove(flowPathStr);
				logger.info("onDataChanged(): removed Flow: " + flowPathStr + "/" + lastFlow);
			}
			thisCmtsNode = thisData.cmtsNode;
			if (thisCmtsNode != null) {
				logger.info("onDataChanged(): removed " + thisData.flowPath + "/" + thisCmtsNode);
				cmtsNodeMap.remove(cmtsId);
				pcmmService.removeCmts(thisCmtsNode);
			}
			break;
		default:
			// we should not be here -- complain bitterly and return
			logger.error("onDataChanged(): Unknown switch change action: " + change);
			return;
		}
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
		// TODO Auto-generated method stub

	}

	@Override
	public void onSessionInitialized(ConsumerContext session) {
		// TODO Auto-generated method stub

	}


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
