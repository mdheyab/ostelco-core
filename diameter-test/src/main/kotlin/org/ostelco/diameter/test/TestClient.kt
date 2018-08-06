package org.ostelco.diameter.test

import org.jdiameter.api.Answer
import org.jdiameter.api.ApplicationId
import org.jdiameter.api.Avp
import org.jdiameter.api.AvpSet
import org.jdiameter.api.Configuration
import org.jdiameter.api.EventListener
import org.jdiameter.api.IllegalDiameterStateException
import org.jdiameter.api.InternalException
import org.jdiameter.api.Message
import org.jdiameter.api.Mode
import org.jdiameter.api.Network
import org.jdiameter.api.NetworkReqListener
import org.jdiameter.api.OverloadException
import org.jdiameter.api.Request
import org.jdiameter.api.RouteException
import org.jdiameter.api.Session
import org.jdiameter.api.SessionFactory
import org.jdiameter.api.Stack
import org.jdiameter.common.impl.app.cca.JCreditControlRequestImpl
import org.jdiameter.server.impl.StackImpl
import org.jdiameter.server.impl.helpers.XMLConfiguration
import org.ostelco.diameter.logger
import org.ostelco.diameter.util.DiameterUtilities
import java.util.concurrent.TimeUnit


class TestClient : EventListener<Request, Answer> {

    private val logger by logger()

    companion object {

        //configuration files
        private const val configFile = "client-jdiameter-config.xml"

        // definition of codes, IDs
        private const val applicationID = 4L  // Diameter Credit Control Application (4)

        private const val commandCode = 272 // Credit-Control
    }

    // The result for the request
    var resultAvps: AvpSet? = null
        private set

    // The resultcode AVP for the request
    var resultCodeAvp: Avp? = null
        private set

    private val authAppId = ApplicationId.createByAuthAppId(applicationID)

    // Diameter stack
    private lateinit var stack: Stack

    // session factory
    private lateinit var factory: SessionFactory

    // set if an answer to a Request has been received
    var isAnswerReceived = false
        private set

    // set if a request has been received
    var isRequestReceived = false
        private set

    // Parse stack configuration
    private lateinit var config: Configuration

    /**
     * Setup Diameter Stack
     *
     * @param configPath path to the jDiameter configuration file
     */
    fun initStack(configPath: String) {
        try {
            config = XMLConfiguration(configPath + configFile)
        } catch (e: Exception) {
            logger.error("Failed to load configuration", e)
        }

        logger.info("Initializing Stack...")
        try {
            this.stack = StackImpl()
            factory = stack.init(config)

            printApplicationInfo()

            //Register network req listener for Re-Auth-Requests
            val network = stack.unwrap<Network>(Network::class.java)
            network.addNetworkReqListener(
                    NetworkReqListener { request ->
                        logger.info("Got a request")
                        resultAvps = request.getAvps()
                        DiameterUtilities().printAvps(resultAvps)
                        isRequestReceived = true
                        null
                    },
                    this.authAppId) //passing our example app id.

        } catch (e: Exception) {
            logger.error("Failed to init Diameter Stack", e)
            this.stack.destroy()
            return
        }

        try {
            logger.info("Starting stack")
            stack.start(Mode.ANY_PEER, 30000, TimeUnit.MILLISECONDS)
            logger.info("Stack is running.")
        } catch (e: Exception) {
            logger.error("Failed to start Diameter Stack", e)
            stack.destroy()
            return
        }

        logger.info("Stack initialization successfully completed.")
    }

    private fun printApplicationInfo() {
        val appIds = stack.metaData.localPeer.commonApplications

        logger.info("Diameter Stack  :: Supporting " + appIds.size + " applications.")
        for (id in appIds) {
            logger.info("Diameter Stack  :: Common :: $id")
        }
    }

    /**
     * Reset Request test
     */
    fun initRequestTest() {
        this.isRequestReceived = false
    }

    /**
     * Create a new Request for the current Session
     *
     * @param destinationRealm Destination Realm
     * @param destinationHost Destination Host
     */
    fun createRequest(destinationRealm : String, destinationHost : String, session : Session): Request? {
        return session.createRequest(
                commandCode,
                ApplicationId.createByAuthAppId(applicationID),
                destinationRealm,
                destinationHost
        )
    }

    /**
     * Create a new DIAMETER session
     */
    fun createSession() : Session? {
        try {
            // FIXME martin: Need better way to make sure the session can be created
            if (!stack.isActive) {
                logger.warn("Stack not active")
            }
            return this.factory.getNewSession("BadCustomSessionId;" + System.currentTimeMillis() + ";0")
        } catch (e: InternalException) {
            logger.error("Start Failed", e)
        } catch (e: InterruptedException) {
            logger.error("Start Failed", e)
        }
        return null
    }

    /**
     * Sends the next request using the current Session.
     *
     * @param request Request to send
     * @return false if send failed
     */
    fun sendNextRequest(request: Request, session: Session?): Boolean {
        isAnswerReceived = false
        if (session != null) {
            val ccr = JCreditControlRequestImpl(request)
            try {
                session.send(ccr.message, this)
                dumpMessage(ccr.message, true) //dump info on console
                return true
            } catch (e: InternalException) {
                logger.error("Failed to send request", e)
            } catch (e: IllegalDiameterStateException) {
                logger.error("Failed to send request", e)
            } catch (e: RouteException) {
                logger.error("Failed to send request", e)
            } catch (e: OverloadException) {
                logger.error("Failed to send request", e)
            }
        } else {
            logger.error("Failed to send request. No session")
        }
        return false
    }

    override fun receivedSuccessMessage(request: Request, answer: Answer) {
        dumpMessage(answer, false)
        resultAvps = answer.avps
        resultCodeAvp = answer.resultCode
        this.isAnswerReceived = true
    }

    override fun timeoutExpired(request: Request) {
        logger.info("Timeout expired $request")
    }


    private fun dumpMessage(message: Message, sending: Boolean) {
        logger.info((if (sending) "Sending " else "Received ")
                + (if (message.isRequest) "Request: " else "Answer: ") + message.commandCode
                + "\nE2E:" + message.endToEndIdentifier
                + "\nHBH:" + message.hopByHopIdentifier
                + "\nAppID:" + message.applicationId)

        logger.info("AVPS[" + message.avps.size() + "]: \n")
    }

    /**
     * Shut down the Diameter Stack
     */
    fun shutdown() {
        try {
            stack.stop(30000, TimeUnit.MILLISECONDS, 0)
        } catch (e: IllegalDiameterStateException) {
            logger.error("Failed to shutdown", e)
        } catch (e: InternalException) {
            logger.error("Failed to shutdown", e)
        }
        stack.destroy()
    }
}