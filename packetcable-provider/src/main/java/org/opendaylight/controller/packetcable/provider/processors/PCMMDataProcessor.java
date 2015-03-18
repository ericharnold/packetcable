/**
 *
 */
package org.opendaylight.controller.packetcable.provider.processors;

import java.net.InetAddress;
import java.net.UnknownHostException;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpPrefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Prefix;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceClassName;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceFlowDirection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.TpProtocol;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.classifier.Classifier;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gate.spec.GateSpec;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.traffic.profile.TrafficProfile;
import org.pcmm.PCMMPdpAgent;
import org.pcmm.PCMMPdpDataProcess;
import org.pcmm.PCMMPdpMsgSender;
import org.pcmm.gates.IAMID;
import org.pcmm.gates.IClassifier;
import org.pcmm.gates.IExtendedClassifier;
import org.pcmm.gates.IGateSpec;
import org.pcmm.gates.ISubscriberID;
import org.pcmm.gates.IGateSpec.Direction;
import org.pcmm.gates.ITrafficProfile;
import org.pcmm.gates.impl.AMID;
//import org.pcmm.gates.impl.BestEffortService;
//import org.pcmm.gates.impl.BestEffortService.BEEnvelop;
import org.pcmm.gates.impl.DOCSISServiceClassNameTrafficProfile;
import org.pcmm.gates.impl.ExtendedClassifier;
import org.pcmm.gates.impl.PCMMGateReq;
import org.pcmm.gates.impl.SubscriberID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 * PacketCable data processor
 *
 */
public class PCMMDataProcessor {

	private Logger logger = LoggerFactory.getLogger(PCMMDataProcessor.class);

	private PCMMGateReq gateReq = new org.pcmm.gates.impl.PCMMGateReq();

	public PCMMDataProcessor() {
		gateReq = new org.pcmm.gates.impl.PCMMGateReq();
		gateReq.setAMID(setAmId(1, 1));
	}

	public PCMMGateReq getGateReq() {
		return gateReq;
	}

	private IAMID setAmId(int amType, int amTag){
		IAMID amid = new org.pcmm.gates.impl.AMID();
        amid.setApplicationType((short)amType);
        amid.setApplicationMgrTag((short)amTag);
        return amid;
	}

	public void build(String qosSubId){
		ISubscriberID subId = new SubscriberID();
		InetAddress inetAddr = null;
		try {
			inetAddr = InetAddress.getByName(qosSubId);
		} catch (UnknownHostException e) {
			logger.error("sendGateSet(): Invalid SubId: " + e);
		}
		subId.setSourceIPAddress(inetAddr);
		gateReq.setSubscriberID(subId);
	}

	public void build(GateSpec qosGateSpec) {
		IGateSpec gateSpec = new org.pcmm.gates.impl.GateSpec();
		if (qosGateSpec.getDirection() != null) {
			ServiceFlowDirection qosDir = qosGateSpec.getDirection();
			Direction dir = null;
			if (qosDir == qosDir.Ds) {
				dir = Direction.DOWNSTREAM;
			} else if (qosDir == qosDir.Us) {
				dir = Direction.UPSTREAM;
			}
			gateSpec.setDirection(dir);
		}
		gateReq.setGateSpec(gateSpec);
	}

	public void build(TrafficProfile qosTrafficProfile) {
		DOCSISServiceClassNameTrafficProfile trafficProfile = new DOCSISServiceClassNameTrafficProfile();
		if (qosTrafficProfile.getServiceClassName() != null) {
			trafficProfile.setServiceClassName(qosTrafficProfile.getServiceClassName().getValue());
		}
		gateReq.setTrafficProfile(trafficProfile);
	}

	private InetAddress getByName(Ipv4Address ipv4){
		InetAddress ipAddress = null;
		try {
			ipAddress = InetAddress.getByName(ipv4.getValue());
		} catch (UnknownHostException e) {
			logger.error(e.getMessage());
		}
		return ipAddress;
	}

	public void build(Classifier qosClassifier) {
		// Legacy classifier
		IClassifier classifier = new org.pcmm.gates.impl.Classifier();
		classifier.setPriority((byte) 64);
		if (qosClassifier.getProtocol() == TpProtocol.Tcp){
			classifier.setProtocol(IClassifier.Protocol.TCP);
		} else if (qosClassifier.getProtocol() == TpProtocol.Udp){
			classifier.setProtocol(IClassifier.Protocol.UDP);
		} else {
			classifier.setProtocol(IClassifier.Protocol.NONE);
		}
		if (qosClassifier.getSrcIp() != null) {
			InetAddress sip = getByName(qosClassifier.getSrcIp());
			if (sip != null) {
				classifier.setSourceIPAddress(sip);
			}
		}
		if (qosClassifier.getDstIp() != null) {
			InetAddress dip = getByName(qosClassifier.getDstIp());
			if (dip != null) {
				classifier.setDestinationIPAddress(dip);
			}
		}
		if (qosClassifier.getSrcPort() != null) {
			classifier.setSourcePort(qosClassifier.getSrcPort().getValue().shortValue());
		}
		if (qosClassifier.getDstPort() != null) {
			classifier.setDestinationPort(qosClassifier.getDstPort().getValue().shortValue());
		}
		if (qosClassifier.getTosByte() != null) {
			classifier.setDSCPTOS(qosClassifier.getTosByte().getValue().byteValue());
			if (qosClassifier.getTosMask() != null) {
				classifier.setDSCPTOSMask(qosClassifier.getTosMask().getValue().byteValue());
			} else {
				// set default TOS mask
				classifier.setDSCPTOSMask((byte)0xff);
			}
		}
		gateReq.setClassifier(classifier);
	}
/*
	private void getTcpMatchRangesValues(TcpMatchRangesAttributes tcpRange, IExtendedClassifier classifier) {
		short srcPortStart, srcPortEnd, dstPortStart, dstPortEnd;
		srcPortStart = srcPortEnd = dstPortStart = dstPortEnd = 0;
		if (tcpRange != null) {
			classifier.setProtocol(IClassifier.Protocol.TCP);
			TcpMatchRanges tcpMatchRanges = tcpRange.getTcpMatchRanges();
			PortNumber tcpDestinationPortStart = tcpMatchRanges.getTcpDestinationPortStart();
			if (tcpDestinationPortStart != null && tcpDestinationPortStart.getValue() != null)
				dstPortStart = tcpDestinationPortStart.getValue().shortValue();
			PortNumber tcpSourcePortStart = tcpMatchRanges.getTcpSourcePortStart();
			if (tcpSourcePortStart != null && tcpSourcePortStart.getValue() != null)
				srcPortStart = tcpSourcePortStart.getValue().shortValue();
			PortNumber tcpDestinationPortEnd = tcpMatchRanges.getTcpDestinationPortEnd();
			if (tcpDestinationPortEnd != null && tcpDestinationPortEnd.getValue() != null)
				dstPortEnd = tcpDestinationPortEnd.getValue().shortValue();
			PortNumber tcpSourcePortEnd = tcpMatchRanges.getTcpSourcePortEnd();
			if (tcpSourcePortEnd != null && tcpSourcePortEnd.getValue() != null)
				srcPortEnd = tcpSourcePortEnd.getValue().shortValue();
		}
		classifier.setDestinationPortStart(dstPortStart);
		classifier.setSourcePortStart(srcPortStart);
		classifier.setDestinationPortEnd(dstPortEnd);
		classifier.setSourcePortEnd(srcPortEnd);
	}

	private void getUdpMatchRangeValues(UdpMatchRangesAttributes udpRange, IExtendedClassifier classifier) {
		short srcPortStart, srcPortEnd, dstPortStart, dstPortEnd;
		srcPortStart = srcPortEnd = dstPortStart = dstPortEnd = 0;
		if (udpRange != null) {
			classifier.setProtocol(IClassifier.Protocol.UDP);
			UdpMatchRanges udpMatchRanges = udpRange.getUdpMatchRanges();
			PortNumber udpDestinationPortStart = udpMatchRanges.getUdpDestinationPortStart();
			if (udpDestinationPortStart != null && udpDestinationPortStart.getValue() != null)
				dstPortStart = udpDestinationPortStart.getValue().shortValue();
			PortNumber udpSourcePortStart = udpMatchRanges.getUdpSourcePortStart();
			if (udpSourcePortStart != null && udpSourcePortStart.getValue() != null)
				srcPortStart = udpSourcePortStart.getValue().shortValue();
			PortNumber udpDestinationPortEnd = udpMatchRanges.getUdpDestinationPortEnd();
			if (udpDestinationPortEnd != null && udpDestinationPortEnd.getValue() != null)
				dstPortEnd = udpDestinationPortEnd.getValue().shortValue();
			PortNumber udpSourcePortEnd = udpMatchRanges.getUdpSourcePortEnd();
			if (udpSourcePortEnd != null && udpSourcePortEnd.getValue() != null)
				srcPortEnd = udpSourcePortEnd.getValue().shortValue();
		}
		classifier.setDestinationPortStart(dstPortStart);
		classifier.setSourcePortStart(srcPortStart);
		classifier.setDestinationPortEnd(dstPortEnd);
		classifier.setSourcePortEnd(srcPortEnd);
	}

	/*
	public ITrafficProfile process(TrafficProfileBestEffortAttributes bestEffort) {
		BestEffortService trafficProfile = new BestEffortService(BestEffortService.DEFAULT_ENVELOP);
		getBEAuthorizedEnvelop(bestEffort, trafficProfile);
		getBEReservedEnvelop(bestEffort, trafficProfile);
		getBECommittedEnvelop(bestEffort, trafficProfile);
		return trafficProfile;
	}


	public ITrafficProfile process(TrafficProfileDocsisServiceClassNameAttributes docsis) {
		DOCSISServiceClassNameTrafficProfile trafficProfile = new DOCSISServiceClassNameTrafficProfile();
		trafficProfile.setServiceClassName(docsis.getServiceClassName());
		return trafficProfile;
	}

	// TODO
	public ITrafficProfile process(TrafficProfileFlowspecAttributes flowSpec) {
		throw new UnsupportedOperationException("Not impelemnted yet");
	}

	public IClassifier process(Match match) {
		ExtendedClassifier classifier = new ExtendedClassifier();
		classifier.setProtocol(IClassifier.Protocol.NONE);
		getUdpMatchRangeValues(match.getAugmentation(UdpMatchRangesRpcAddFlow.class), classifier);
		getTcpMatchRangesValues(match.getAugmentation(TcpMatchRangesRpcAddFlow.class), classifier);
		SubscriberIdRpcAddFlow subId = match.getAugmentation(SubscriberIdRpcAddFlow.class);
		Ipv6Address ipv6Address = subId.getSubscriberId().getIpv6Address();
		if (ipv6Address != null)
			try {
				classifier.setDestinationIPAddress(InetAddress.getByName(ipv6Address.getValue()));
			} catch (UnknownHostException e) {
				logger.error(e.getMessage());
			}

		Ipv4Address ipv4Address = subId.getSubscriberId().getIpv4Address();
		if (ipv4Address != null)
			try {
				classifier.setDestinationIPAddress(InetAddress.getByName(ipv4Address.getValue()));
			} catch (UnknownHostException e) {
				logger.error(e.getMessage());
			}
		return classifier;
	}

	private void getBECommittedEnvelop(TrafficProfileBestEffortAttributes bestEffort, BestEffortService trafficProfile) {
		BEEnvelop committedEnvelop = trafficProfile.getCommittedEnvelop();
		BeCommittedEnvelope beCommittedEnvelope = bestEffort.getBeCommittedEnvelope();
		if (beCommittedEnvelope.getTrafficPriority() != null)
			committedEnvelop.setTrafficPriority(beCommittedEnvelope.getTrafficPriority().byteValue());
		else
			committedEnvelop.setTrafficPriority(BestEffortService.DEFAULT_TRAFFIC_PRIORITY);
		if (beCommittedEnvelope.getMaximumTrafficBurst() != null)
			committedEnvelop.setMaximumTrafficBurst(beCommittedEnvelope.getMaximumTrafficBurst().intValue());
		else
			committedEnvelop.setMaximumTrafficBurst(BestEffortService.DEFAULT_MAX_TRAFFIC_BURST);
		if (beCommittedEnvelope.getRequestTransmissionPolicy() != null)
			committedEnvelop.setRequestTransmissionPolicy(beCommittedEnvelope.getRequestTransmissionPolicy().intValue());
		// else
		// committedEnvelop.setRequestTransmissionPolicy(PCMMGlobalConfig.BETransmissionPolicy);
		if (beCommittedEnvelope.getMaximumSustainedTrafficRate() != null)
			committedEnvelop.setMaximumSustainedTrafficRate(beCommittedEnvelope.getMaximumSustainedTrafficRate().intValue());
		// else
		// committedEnvelop.setMaximumSustainedTrafficRate(PCMMGlobalConfig.DefaultLowBestEffortTrafficRate);
	}

	private void getBEReservedEnvelop(TrafficProfileBestEffortAttributes bestEffort, BestEffortService trafficProfile) {
		BEEnvelop reservedEnvelop = trafficProfile.getReservedEnvelop();
		BeReservedEnvelope beReservedEnvelope = bestEffort.getBeReservedEnvelope();
		if (beReservedEnvelope.getTrafficPriority() != null)
			reservedEnvelop.setTrafficPriority(beReservedEnvelope.getTrafficPriority().byteValue());
		else
			reservedEnvelop.setTrafficPriority(BestEffortService.DEFAULT_TRAFFIC_PRIORITY);
		if (beReservedEnvelope.getMaximumTrafficBurst() != null)
			reservedEnvelop.setMaximumTrafficBurst(beReservedEnvelope.getMaximumTrafficBurst().intValue());
		else
			reservedEnvelop.setMaximumTrafficBurst(BestEffortService.DEFAULT_MAX_TRAFFIC_BURST);
		if (beReservedEnvelope.getRequestTransmissionPolicy() != null)
			reservedEnvelop.setRequestTransmissionPolicy(beReservedEnvelope.getRequestTransmissionPolicy().intValue());
		if (beReservedEnvelope.getMaximumSustainedTrafficRate() != null)
			reservedEnvelop.setMaximumSustainedTrafficRate(beReservedEnvelope.getMaximumSustainedTrafficRate().intValue());
	}

	private void getBEAuthorizedEnvelop(TrafficProfileBestEffortAttributes bestEffort, BestEffortService trafficProfile) {
		BEEnvelop authorizedEnvelop = trafficProfile.getAuthorizedEnvelop();
		BeAuthorizedEnvelope beAuthorizedEnvelope = bestEffort.getBeAuthorizedEnvelope();
		if (beAuthorizedEnvelope.getTrafficPriority() != null)
			authorizedEnvelop.setTrafficPriority(beAuthorizedEnvelope.getTrafficPriority().byteValue());
		else
			authorizedEnvelop.setTrafficPriority(BestEffortService.DEFAULT_TRAFFIC_PRIORITY);
		if (beAuthorizedEnvelope.getMaximumTrafficBurst() != null)
			authorizedEnvelop.setMaximumTrafficBurst(beAuthorizedEnvelope.getMaximumTrafficBurst().intValue());
		else
			authorizedEnvelop.setMaximumTrafficBurst(BestEffortService.DEFAULT_MAX_TRAFFIC_BURST);
		if (beAuthorizedEnvelope.getRequestTransmissionPolicy() != null)
			authorizedEnvelop.setRequestTransmissionPolicy(beAuthorizedEnvelope.getRequestTransmissionPolicy().intValue());
		if (beAuthorizedEnvelope.getMaximumSustainedTrafficRate() != null)
			authorizedEnvelop.setMaximumSustainedTrafficRate(beAuthorizedEnvelope.getMaximumSustainedTrafficRate().intValue());
	}

	private void getTcpMatchRangesValues(TcpMatchRangesAttributes tcpRange, IExtendedClassifier classifier) {
		short srcPortStart, srcPortEnd, dstPortStart, dstPortEnd;
		srcPortStart = srcPortEnd = dstPortStart = dstPortEnd = 0;
		if (tcpRange != null) {
			classifier.setProtocol(IClassifier.Protocol.TCP);
			TcpMatchRanges tcpMatchRanges = tcpRange.getTcpMatchRanges();
			PortNumber tcpDestinationPortStart = tcpMatchRanges.getTcpDestinationPortStart();
			if (tcpDestinationPortStart != null && tcpDestinationPortStart.getValue() != null)
				dstPortStart = tcpDestinationPortStart.getValue().shortValue();
			PortNumber tcpSourcePortStart = tcpMatchRanges.getTcpSourcePortStart();
			if (tcpSourcePortStart != null && tcpSourcePortStart.getValue() != null)
				srcPortStart = tcpSourcePortStart.getValue().shortValue();
			PortNumber tcpDestinationPortEnd = tcpMatchRanges.getTcpDestinationPortEnd();
			if (tcpDestinationPortEnd != null && tcpDestinationPortEnd.getValue() != null)
				dstPortEnd = tcpDestinationPortEnd.getValue().shortValue();
			PortNumber tcpSourcePortEnd = tcpMatchRanges.getTcpSourcePortEnd();
			if (tcpSourcePortEnd != null && tcpSourcePortEnd.getValue() != null)
				srcPortEnd = tcpSourcePortEnd.getValue().shortValue();
		}
		classifier.setDestinationPortStart(dstPortStart);
		classifier.setSourcePortStart(srcPortStart);
		classifier.setDestinationPortEnd(dstPortEnd);
		classifier.setSourcePortEnd(srcPortEnd);
	}

	private void getUdpMatchRangeValues(UdpMatchRangesAttributes udpRange, IExtendedClassifier classifier) {
		short srcPortStart, srcPortEnd, dstPortStart, dstPortEnd;
		srcPortStart = srcPortEnd = dstPortStart = dstPortEnd = 0;
		if (udpRange != null) {
			classifier.setProtocol(IClassifier.Protocol.UDP);
			UdpMatchRanges udpMatchRanges = udpRange.getUdpMatchRanges();
			PortNumber udpDestinationPortStart = udpMatchRanges.getUdpDestinationPortStart();
			if (udpDestinationPortStart != null && udpDestinationPortStart.getValue() != null)
				dstPortStart = udpDestinationPortStart.getValue().shortValue();
			PortNumber udpSourcePortStart = udpMatchRanges.getUdpSourcePortStart();
			if (udpSourcePortStart != null && udpSourcePortStart.getValue() != null)
				srcPortStart = udpSourcePortStart.getValue().shortValue();
			PortNumber udpDestinationPortEnd = udpMatchRanges.getUdpDestinationPortEnd();
			if (udpDestinationPortEnd != null && udpDestinationPortEnd.getValue() != null)
				dstPortEnd = udpDestinationPortEnd.getValue().shortValue();
			PortNumber udpSourcePortEnd = udpMatchRanges.getUdpSourcePortEnd();
			if (udpSourcePortEnd != null && udpSourcePortEnd.getValue() != null)
				srcPortEnd = udpSourcePortEnd.getValue().shortValue();
		}
		classifier.setDestinationPortStart(dstPortStart);
		classifier.setSourcePortStart(srcPortStart);
		classifier.setDestinationPortEnd(dstPortEnd);
		classifier.setSourcePortEnd(srcPortEnd);
	}
*/
}
