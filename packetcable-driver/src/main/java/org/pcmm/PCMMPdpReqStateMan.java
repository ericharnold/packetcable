/**

 * Copyright (c) 2014 CableLabs.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html

 */

package org.pcmm;

import java.net.Socket;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Vector;

import org.pcmm.gates.IGateID;
import org.pcmm.gates.IPCMMError;
import org.pcmm.gates.IPCMMGate;
import org.pcmm.gates.ITransactionID;
import org.pcmm.gates.impl.PCMMError;
import org.pcmm.gates.impl.PCMMGateReq;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.umu.cops.common.COPSDebug;
import org.umu.cops.prpdp.COPSPdpException;
import org.umu.cops.stack.COPSClientSI;
import org.umu.cops.stack.COPSContext;
import org.umu.cops.stack.COPSData;
import org.umu.cops.stack.COPSDeleteMsg;
import org.umu.cops.stack.COPSError;
import org.umu.cops.stack.COPSHandle;
import org.umu.cops.stack.COPSHeader;
import org.umu.cops.stack.COPSPrObjBase;
import org.umu.cops.stack.COPSReportMsg;
import org.umu.cops.stack.COPSReportType;
import org.umu.cops.stack.COPSReqMsg;
import org.umu.cops.stack.COPSSyncStateMsg;





/**
 * State manager class for provisioning requests, at the PDP side.
 */
public class PCMMPdpReqStateMan {

	private static final Logger logger = LoggerFactory.getLogger(PCMMPdpReqStateMan.class);


    /**
     * Request State created
     */
    public final static short ST_CREATE = 1;
    /**
     * Request received
     */
    public final static short ST_INIT = 2;
    /**
     * Decisions sent
     */
    public final static short ST_DECS = 3;
    /**
     * Report received
     */
    public final static short ST_REPORT = 4;
    /**
     * Request State finalized
     */
    public final static short ST_FINAL = 5;
    /**
     * New Request State solicited
     */
    public final static short ST_NEW = 6;
    /**
     * Delete Request State solicited
     */
    public final static short ST_DEL = 7;
    /**
     * SYNC request sent
     */
    public final static short ST_SYNC = 8;
    /**
     * SYNC completed
     */
    public final static short ST_SYNCALL = 9;
    /**
     * Close connection received
     */
    public final static short ST_CCONN = 10;
    /**
     * Keep-alive timeout
     */
    public final static short ST_NOKA = 11;
    /**
     * Accounting timeout
     */
    public final static short ST_ACCT = 12;

    /**
     * COPS client-type that identifies the policy client
     */
    protected short _clientType;

    /**
     *  COPS client handle used to uniquely identify a particular
     *  PEP's request for a client-type
     */
    protected COPSHandle _handle;

    /**
     * Object for performing policy data processing
     */
    protected PCMMPdpDataProcess _process;

    /**
     *  Current state of the request being managed
     */
    protected short _status;

    /** COPS message transceiver used to send COPS messages */
    protected PCMMPdpMsgSender _sender;

    /**
     * Creates a request state manager
     * @param clientType    Client-type
     * @param clientHandle  Client handle
     */
    public PCMMPdpReqStateMan(short clientType, String clientHandle) {
        // COPS Handle
        _handle = new COPSHandle();
        COPSData id = new COPSData(clientHandle);
        _handle.setId(id);
        // client-type
        _clientType = clientType;

        _status = ST_CREATE;
    }

    /**
     * Gets the client handle
     * @return   Client's <tt>COPSHandle</tt>
     */
    public COPSHandle getClientHandle() {
        return _handle;
    }

    /**
     * Gets the client-type
     * @return   Client-type value
     */
    public short getClientType() {
        return _clientType;
    }

    /**
     * Gets the status of the request
     * @return      Request state value
     */
    public short getStatus() {
        return _status;
    }

    /**
     * Gets the policy data processing object
     * @return   Policy data processing object
     */
    public PCMMPdpDataProcess getDataProcess() {
        return _process;
    }

    /**
     * Sets the policy data processing object
     * @param   process Policy data processing object
     */
    public void setDataProcess(PCMMPdpDataProcess process) {
        _process = process;
    }

    /**
     * Called when COPS sync is completed
     * @param    repMsg              COPS sync message
     * @throws   COPSPdpException
     */
    protected void processSyncComplete(COPSSyncStateMsg repMsg)
    throws COPSPdpException {

        _status = ST_SYNCALL;

        // maybe we should notifySyncComplete ...
    }

    /**
     * Initializes a new request state over a socket
     * @param sock  Socket to the PEP
     * @throws COPSPdpException
     */
    protected void initRequestState(Socket sock)
    throws COPSPdpException {
        // Inits an object for sending COPS messages to the PEP
        _sender = new PCMMPdpMsgSender(_clientType, _handle, sock);

        // Initial state
        _status = ST_INIT;
    }



    /**
     * Processes a COPS request
     * @param msg   COPS request received from the PEP
     * @throws COPSPdpException
     */
    protected void processRequest(COPSReqMsg msg)
    throws COPSPdpException {

        COPSHeader hdrmsg = msg.getHeader();
        COPSHandle handlemsg = msg.getClientHandle();
        COPSContext contextmsg = msg.getContext();

        //** Analyze the request
        //**

        /* <Request> ::= <Common Header>
        *                   <Client Handle>
        *                   <Context>
        *                   *(<Named ClientSI>)
        *                   [<Integrity>]
        * <Named ClientSI> ::= <*(<PRID> <EPD>)>
        *
        * Very important, this is actually being treated like this:
        * <Named ClientSI> ::= <PRID> | <EPD>
        *

        // Named ClientSI
        Vector clientSIs = msg.getClientSI();
        Hashtable reqSIs = new Hashtable(40);
        String strobjprid = new String();
        for (Enumeration e = clientSIs.elements() ; e.hasMoreElements() ;) {
            COPSClientSI clientSI = (COPSClientSI) e.nextElement();

            COPSPrObjBase obj = new COPSPrObjBase(clientSI.getData().getData());
            switch (obj.getSNum())
            {
                case COPSPrObjBase.PR_PRID:
                    strobjprid = obj.getData().str();
                    break;
                case COPSPrObjBase.PR_EPD:
                    reqSIs.put(strobjprid, obj.getData().str());
                    // COPSDebug.out(getClass().getName(),"PRID: " + strobjprid);
                    // COPSDebug.out(getClass().getName(),"EPD: " + obj.getData().str());
                    break;
                default:
                    break;
            }
        }

        //** Here we must retrieve a decision depending on
        //** the supplied ClientSIs
        // reqSIs is a hashtable with the prid and epds

        // ................
        //
        Hashtable removeDecs = new Hashtable();
        Hashtable installDecs = new Hashtable();
        _process.setClientData(this, reqSIs);

        removeDecs = _process.getRemovePolicy(this);
        installDecs = _process.getInstallPolicy(this);

        //** We create the SOLICITED decision
        //**
        _sender.sendDecision(removeDecs, installDecs);
        _status = ST_DECS;
        */
    }

    /**
     * Processes a report
     * @param msg   Report message from the PEP
     * @throws COPSPdpException
     */
    protected void processReport(COPSReportMsg msg)
    		throws COPSPdpException {

        //** Analyze the report
        //**

        /*
           * <Report State> ::= <Common Header>
           *                        <Client Handle>
          *                     <Report Type>
         *                      *(<Named ClientSI>)
          *                     [<Integrity>]
         * <Named ClientSI: Report> ::= <[<GPERR>] *(<report>)>
         * <report> ::= <ErrorPRID> <CPERR> *(<PRID><EPD>)
         *
         * Important, <Named ClientSI> is not parsed
        */

        // COPSHeader hdrmsg = msg.getHeader();
        // COPSHandle handlemsg = msg.getClientHandle();

        // WriteBinaryDump("COPSReportMessage", msg.getData().getData());
        // Report Type
        COPSReportType rtypemsg = msg.getReport();

        // Named ClientSI
        Vector clientSIs = msg.getClientSI();
        COPSClientSI myclientSI = (COPSClientSI) msg.getClientSI().elementAt(0);
        byte[] data = Arrays.copyOfRange(myclientSI.getData().getData(), 0, myclientSI.getData().getData().length );

        // PCMMUtils.WriteBinaryDump("COPSReportClientSI", data);
        logger.debug("PCMMGateReq Parse Gate Message");
        PCMMGateReq gateMsg = new PCMMGateReq(data);

        Hashtable repSIs = new Hashtable(40);
        String strobjprid = new String();
        for (Enumeration e = clientSIs.elements() ; e.hasMoreElements() ;) {
            COPSClientSI clientSI = (COPSClientSI) e.nextElement();

            COPSPrObjBase obj = new COPSPrObjBase(clientSI.getData().getData());
            switch (obj.getSNum()) {
            case COPSPrObjBase.PR_PRID:
                logger.debug("COPSPrObjBase.PR_PRID");
                strobjprid = obj.getData().str();
                break;
            case COPSPrObjBase.PR_EPD:
                logger.debug("COPSPrObjBase.PR_EPD");
                repSIs.put(strobjprid, obj.getData().str());
                // COPSDebug.out(getClass().getName(),"PRID: " + strobjprid);
                // COPSDebug.out(getClass().getName(),"EPD: " + obj.getData().str());
                break;
            default:
                COPSDebug.err(getClass().getName(),"Object s-num: " + obj.getSNum() + "stype " + obj.getSType());
                COPSDebug.err(getClass().getName(),"PRID: " + strobjprid);
                COPSDebug.err(getClass().getName(),"EPD: " + obj.getData().str());
                break;
            }
        }

        logger.debug("rtypemsg process");
        //** Here we must act in accordance with
        //** the report received

        // retrieve and remove the transactionId to gate request map entry
    	// see PCMMPdpMsgSender.sendGateSet(IPCMMGate gate)
    	ITransactionID trID = gateMsg.getTransactionID();
        Short trIDnum = trID.getTransactionIdentifier();
    	IPCMMGate gate = PCMMGlobalConfig.transactionGateMap.remove(trIDnum);
    	if (gate != null) {
        	// capture the "error" message if any
           	gate.setError(gateMsg.getError());
    	}else {
    		logger.error("processReport(): gateReq not found for transactionID {}", trIDnum);
    		return;
    	}

    	if (rtypemsg.isSuccess()) {
        	logger.debug("rtypemsg success");
            _status = ST_REPORT;
            if (_process != null)
            	_process.successReport(this, gateMsg);
            else {
            	String cmdType = "";
            	if ( trID.getGateCommandType() == ITransactionID.GateDeleteAck ) {
            		cmdType = "GateDeleteAck";
            	} else if ( trID.getGateCommandType() == ITransactionID.GateSetAck ) {
            		cmdType = "GateSetAck";
            	}
            	// capture the gateId from the response message
            	IGateID gateID = gateMsg.getGateID();
            	gate.setGateID(gateID);
            	int gateIdInt = gateID.getGateID();
            	String gateIdHex = String.format("%08x", gateIdInt);
             	logger.debug(getClass().getName() + ": " + cmdType + ": GateID = " + gateIdHex);
            }
        } else if (rtypemsg.isFailure()) {
	        logger.debug("rtypemsg failure");
	            _status = ST_REPORT;
	            if (_process != null)
	            	_process.failReport(this, gateMsg);
				else {
					gate.setError(gateMsg.getError());
					logger.warn(getClass().getName()+ ": " + gateMsg.getError().toString());
				}

        } else if (rtypemsg.isAccounting()) {
        	logger.debug("rtypemsg account");
            _status = ST_ACCT;
            if (_process != null)
            	_process.acctReport(this, gateMsg);
        }
    	logger.debug("gate.notify()");
     	// let the waiting gateSet/gateDelete sender proceed
    	synchronized(gate) {
    		gate.notify();
    	}
    	logger.debug("Out processReport");
    }

    /**
    * Called when connection is closed
    * @param error  Reason
    * @throws COPSPdpException
    */
    protected void processClosedConnection(COPSError error)
    throws COPSPdpException {
        if (_process != null)
            _process.notifyClosedConnection(this, error);

        _status = ST_CCONN;
    }

    /**
     * Called when no keep-alive is received
     * @throws COPSPdpException
     */
    protected void processNoKAConnection()
    throws COPSPdpException {
        if (_process != null)
            _process.notifyNoKAliveReceived(this);

        _status = ST_NOKA;
    }

    /**
    * Deletes the request state
    * @throws COPSPdpException
    */
    protected void finalizeRequestState()
    throws COPSPdpException {
        _sender.sendDeleteRequestState();
        _status = ST_FINAL;
    }

    /**
    * Asks for a COPS sync
    * @throws COPSPdpException
    */
    protected void syncRequestState()
    throws COPSPdpException {
        _sender.sendSyncRequestState();
        _status = ST_SYNC;
    }

    /**
     * Opens a new request state
     * @throws COPSPdpException
     */
    protected void openNewRequestState()
    throws COPSPdpException {
        _sender.sendOpenNewRequestState();
        _status = ST_NEW;
    }

    /**
     * Processes a COPS delete message
     * @param dMsg  <tt>COPSDeleteMsg</tt> received from the PEP
     * @throws COPSPdpException
     */
    protected void processDeleteRequestState(COPSDeleteMsg dMsg)
    throws COPSPdpException {
        if (_process != null)
            _process.closeRequestState(this);

        _status = ST_DEL;
    }

}
