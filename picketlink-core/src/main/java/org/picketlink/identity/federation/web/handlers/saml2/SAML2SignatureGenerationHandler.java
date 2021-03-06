/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2008, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.picketlink.identity.federation.web.handlers.saml2;

import static org.picketlink.identity.federation.core.util.StringUtil.isNotNull;

import javax.xml.namespace.QName;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import org.picketlink.identity.federation.api.saml.v2.sig.SAML2Signature;
import org.picketlink.identity.federation.core.exceptions.ConfigurationException;
import org.picketlink.identity.federation.core.exceptions.ProcessingException;
import org.picketlink.identity.federation.core.saml.v2.interfaces.SAML2HandlerRequest;
import org.picketlink.identity.federation.core.saml.v2.interfaces.SAML2HandlerResponse;
import org.picketlink.identity.federation.core.saml.v2.util.DocumentUtil;
import org.picketlink.identity.federation.web.constants.GeneralConstants;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLConstants;
import org.picketlink.identity.federation.core.saml.v2.constants.JBossSAMLURIConstants;
/*
import org.picketlink.identity.federation.web.constants.JBossSAMLConstants;
import org.picketlink.identity.federation.web.constants.JBossSAMLURIConstants;
*/
import org.picketlink.identity.federation.web.util.RedirectBindingSignatureUtil;
import org.picketlink.identity.federation.web.util.RedirectBindingUtil;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

/**
 * Handles SAML2 Signature
 * 
 * @author Anil.Saldhana@redhat.com
 * @since Oct 12, 2009
 */
public class SAML2SignatureGenerationHandler extends AbstractSignatureHandler {

    public static final String SIGN_ASSERTION_ONLY = "SIGN_ASSERTION_ONLY";
    public static final String SIGN_RESPONSE_AND_ASSERTION = "SIGN_RESPONSE_AND_ASSERTION";

    @Override
    public void generateSAMLRequest(SAML2HandlerRequest request, SAML2HandlerResponse response) throws ProcessingException {
        // Generate the signature
        Document samlDocument = response.getResultingDocument();

        if (samlDocument == null) {
            logger.trace("No document generated in the handler chain. Cannot generate signature");
            return;
        }

        this.sign(samlDocument, request, response);
    }

    public void handleRequestType(SAML2HandlerRequest request, SAML2HandlerResponse response) throws ProcessingException {
        Document responseDocument = response.getResultingDocument();

        if (responseDocument == null) {
            logger.trace("No response document found");
            return;
        }

        this.sign(responseDocument, request, response);
    }

    @Override
    public void handleStatusResponseType(SAML2HandlerRequest request, SAML2HandlerResponse response) throws ProcessingException {
        Document responseDocument = response.getResultingDocument();
        if (responseDocument == null) {
            logger.trace("No response document found");
            return;
        }

        this.sign(responseDocument, request, response);
    }

    private void sign(Document samlDocument, SAML2HandlerRequest request, SAML2HandlerResponse response)
            throws ProcessingException {
        if (!isSupportsSignature(request)) {
            return;
        }

        // Get the Key Pair
        KeyPair keypair = (KeyPair) this.handlerChainConfig.getParameter(GeneralConstants.KEYPAIR);
        X509Certificate x509Certificate = (X509Certificate) this.handlerChainConfig.getParameter(GeneralConstants.X509CERTIFICATE);

        if (keypair == null) {
            logger.samlHandlerKeyPairNotFound();
            throw logger.samlHandlerKeyPairNotFoundError();
        }

        if (isSAMLResponse(samlDocument)) {
            if (isSignAssertionOnly() || isSignResponseAndAssertion()) {
                Element originalAssertionElement = DocumentUtil.getChildElement(samlDocument.getDocumentElement(), new QName(JBossSAMLURIConstants.ASSERTION_NSURI.get(), JBossSAMLConstants.ASSERTION.get()));
                Node clonedAssertionElement = originalAssertionElement.cloneNode(true);
                Document temporaryDocument;

                try {
                    temporaryDocument = DocumentUtil.createDocument();
                } catch (ConfigurationException e) {
                    throw this.logger.processingError(e);
                }

                temporaryDocument.adoptNode(clonedAssertionElement);
                temporaryDocument.appendChild(clonedAssertionElement);

                logger.trace("Going to sign assertion within response document.");
                signDocument(temporaryDocument, keypair, x509Certificate);

                samlDocument.adoptNode(clonedAssertionElement);

                Element parentNode = (Element) originalAssertionElement.getParentNode();

                parentNode.replaceChild(clonedAssertionElement, originalAssertionElement);
            }

            if (!isSignAssertionOnly()) {
                signDocument(samlDocument, keypair, x509Certificate);
            }
        } else {
            signDocument(samlDocument, keypair, x509Certificate);
        }

        if (!response.isPostBindingForResponse()) {
            logger.trace("Going to sign response document with REDIRECT binding type");
            String destinationQueryString = signRedirect(samlDocument, response.getRelayState(), keypair,
                    response.getSendRequest());
            response.setDestinationQueryStringWithSignature(destinationQueryString);
        }
    }

    private boolean isSAMLResponse(final Document samlDocument) {
        return samlDocument.getDocumentElement().getLocalName().equals(JBossSAMLConstants.RESPONSE.get());
    }

    private boolean isSignAssertionOnly() {
        return this.handlerConfig.getParameter(SIGN_ASSERTION_ONLY) != null ? Boolean.valueOf(this.handlerConfig.getParameter(SIGN_ASSERTION_ONLY).toString()) : false;
    }

    private boolean isSignResponseAndAssertion() {
        return this.handlerConfig.getParameter(SIGN_RESPONSE_AND_ASSERTION) != null ? Boolean.valueOf(this.handlerConfig.getParameter(SIGN_RESPONSE_AND_ASSERTION).toString()) : false;
    }

    private void signDocument(Document samlDocument, KeyPair keypair, X509Certificate x509Certificate) throws ProcessingException {
        SAML2Signature samlSignature = new SAML2Signature();
        Node nextSibling = samlSignature.getNextSiblingOfIssuer(samlDocument);
        samlSignature.setNextSibling(nextSibling);
        if(x509Certificate != null){
            samlSignature.setX509Certificate(x509Certificate);
        }
        samlSignature.signSAMLDocument(samlDocument, keypair);
    }

    private String signRedirect(Document samlDocument, String relayState, KeyPair keypair,
                                boolean willSendRequest)
            throws ProcessingException {
        try {
            String samlMessage = DocumentUtil.getDocumentAsString(samlDocument);
            String base64Request = RedirectBindingUtil.deflateBase64URLEncode(samlMessage.getBytes("UTF-8"));
            PrivateKey signingKey = keypair.getPrivate();

            String url;

            // Encode relayState before signing
            if (isNotNull(relayState))
                relayState = RedirectBindingUtil.urlEncode(relayState);

            if (willSendRequest) {
                url = RedirectBindingSignatureUtil.getSAMLRequestURLWithSignature(base64Request, relayState, signingKey);
            } else {
                url = RedirectBindingSignatureUtil.getSAMLResponseURLWithSignature(base64Request, relayState, signingKey);
            }

            return url;
        } catch (ConfigurationException ce) {
            logger.samlHandlerErrorSigningRedirectBindingMessage(ce);
            throw logger.samlHandlerSigningRedirectBindingMessageError(ce);
        } catch (GeneralSecurityException ce) {
            logger.samlHandlerErrorSigningRedirectBindingMessage(ce);
            throw logger.samlHandlerSigningRedirectBindingMessageError(ce);
        } catch (IOException ce) {
            logger.samlHandlerErrorSigningRedirectBindingMessage(ce);
            throw logger.samlHandlerSigningRedirectBindingMessageError(ce);
        }
    }
}
