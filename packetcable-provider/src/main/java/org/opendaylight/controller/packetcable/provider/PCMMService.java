package org.opendaylight.controller.packetcable.provider;

import java.io.IOException;
import java.util.Map;

import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.IpAddress;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv4Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.Ipv6Address;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev100924.PortNumber;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.Ccaps;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceClassName;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.ServiceFlowDirection;
import org.opendaylight.yang.gen.v1.urn.packetcable.rev150314.pcmm.qos.gates.apps.subs.Gates;
import org.pcmm.PCMMDef;
import org.pcmm.PCMMPdpAgent;
import org.pcmm.PCMMPdpDataProcess;
import org.pcmm.PCMMPdpMsgSender;
import org.pcmm.gates.impl.PCMMGateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umu.cops.prpdp.COPSPdpException;
import org.umu.cops.stack.COPSException;

import com.google.common.collect.Maps;

public class PCMMService {
	private Logger logger = LoggerFactory.getLogger(PCMMService.class);

	private Map<Ccaps, CcapClient> ccapClients = Maps.newConcurrentMap();
	private Map<String, PCMMGateReq> gateRequests = Maps.newConcurrentMap();

	public PCMMService() {
	}

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
            logger.debug("CcapClient: connect(): {}:{}", ipv4, port);
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
            logger.debug("CcapClient: disconnect(): {}:{}", ipv4, port);
        	try {
				pcmmPdp.disconnect(pcmmPdp.getPepIdString(), null);
				isConnected = false;
			} catch (COPSException | IOException e) {
                logger.error("CcapClient: disconnect(): {}:{} FAILED: {}", ipv4, port, e.getMessage());
			}
        }

        public Boolean sendGateSet(PCMMGateReq gateReq) {
            logger.debug("CcapClient: sendGateSet(): {}:{} => {}", ipv4, port, gateReq);
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
            logger.debug("CcapClient: sendGateDelete(): {}:{} => {}", ipv4, port, gateReq);
        	try {
                pcmmSender = new PCMMPdpMsgSender(PCMMDef.C_PCMM, pcmmPdp.getClientHandle(), pcmmPdp.getSocket());
				pcmmSender.sendGateDelete(gateReq);
			} catch (COPSPdpException e) {
                logger.error("CcapClient: sendGateDelete(): {}:{} => {} FAILED: {}", ipv4, port, gateReq, e.getMessage());
			}
			return true;
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

	public String addCcap(Ccaps ccap) {
		String ccapId = ccap.getCcapId();
		IpAddress ipAddr = ccap.getConnection().getIpAddress();
		PortNumber portNum = ccap.getConnection().getPort();
		int port = portNum.getValue();
		String ipv4 = ipAddr.getIpv4Address().getValue();
		logger.info("addCcap() {} @ {}:{}", ccapId, ipv4, port);
		CcapClient client = new CcapClient();
		client.connect(ipAddr, portNum);
		String responseStr = null;
		if (client.isConnected) {
			ccapClients.put(ccap, client);
			logger.info("addCcap(): connected: {} @ {}:{}", ccapId, ipv4, port);
			responseStr = String.format("200 OK - CCAP %s connected @ %s:%d", ccapId, ipv4, port);
		} else {
			responseStr = String.format("404 Not Found - CCAP %s failed to connect @ %s:%d - %s",
										ccapId, ipv4, port, client.errMessage);
		}
		return responseStr;
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

	public String sendGateSet(Ccaps ccap, String gatePathStr, IpAddress qosSubId, Gates qosGate, ServiceFlowDirection scnDir) {
		String responseStr = null;
		CcapClient ccapClient = ccapClients.get(ccap);
		// assemble the gate request for this subId
		PCMMGateReqBuilder gateBuilder = new PCMMGateReqBuilder();
		gateBuilder.build(ccap.getAmId());
		gateBuilder.build(getIpAddressStr(qosSubId));
		// force gateSpec.Direction to align with SCN direction
		ServiceClassName scn = qosGate.getTrafficProfile().getServiceClassName();
		if (scn != null) {
			gateBuilder.build(qosGate.getGateSpec(), scnDir);
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
			responseStr = String.format("408 Request Timeout - gate response timeout exceeded for %s/%s",
					ccap.getCcapId(), gatePathStr);
			return responseStr;
		}
		if (gateReq.getError() != null) {
			logger.error("PCMMService: sendGateSet(): returned error: {}",
					gateReq.getError().toString());
			responseStr = String.format("404 Not Found - sendGateSet for %s/%s returned error - %s",
					ccap.getCcapId(), gatePathStr, gateReq.getError().toString());
			return responseStr;
		} else {
			if (gateReq.getGateID() != null) {
				logger.info(String.format("PCMMService: sendGateSet(): returned GateId %08x: ",
						gateReq.getGateID().getGateID()));
				responseStr = String.format("200 OK - sendGateSet for %s/%s returned GateId %08x",
						ccap.getCcapId(), gatePathStr, gateReq.getGateID().getGateID());
			} else {
				logger.info("PCMMService: sendGateSet(): no gateId returned:");
				responseStr = String.format("404 Not Found - sendGateSet for %s/%s no gateId returned",
						ccap.getCcapId(), gatePathStr);
			}
			return responseStr;
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
					logger.error("PCMMService: sendGateDelete(): deleted but no gateId returned");
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

